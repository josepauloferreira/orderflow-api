package io.github.josepauloferreira.orderflow.ordering.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.josepauloferreira.orderflow.ordering.api.dto.CreateOrderRequest;
import io.github.josepauloferreira.orderflow.ordering.application.OrderService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class SecurityIdempotencyIntegrationTests {

  @Container @ServiceConnection
  static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private OrderService orders;
  @Autowired private JwtAuthenticationConverter converter;

  @BeforeEach
  void reset() {
    jdbc.update("DELETE FROM order_idempotency");
    jdbc.update("DELETE FROM stock_movements");
    jdbc.update("DELETE FROM order_items");
    jdbc.update("DELETE FROM customer_orders");
    jdbc.update("DELETE FROM inventory");
    jdbc.update("DELETE FROM products");
  }

  @Test
  void permitsPublicCatalogButProtectsInventoryAndAdministrativeWrites() throws Exception {
    mvc.perform(get("/api/v1/products")).andExpect(status().isOk());
    mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    mvc.perform(get("/api/v1/products/" + UUID.randomUUID() + "/stock"))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[]}"))
        .andExpect(status().isUnauthorized());

    mvc.perform(
            post("/api/v1/products")
                .with(customer("customer-a"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\":\"S-100\",\"name\":\"Item\",\"price\":10.00}"))
        .andExpect(status().isForbidden());

    mvc.perform(
            post("/api/v1/products")
                .with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\":\"S-100\",\"name\":\"Item\",\"price\":10.00}"))
        .andExpect(status().isCreated());
  }

  @Test
  void restrictsCustomerAccessToOwnOrdersButAllowsAdminReview() throws Exception {
    UUID product = seed("SEC-100", 3);
    UUID order = createOrder(product, "customer-a", "order-key-0001", 1);

    mvc.perform(get("/api/v1/orders/" + order).with(customer("customer-a")))
        .andExpect(status().isOk());
    mvc.perform(get("/api/v1/orders/" + order).with(customer("customer-b")))
        .andExpect(status().isForbidden());
    mvc.perform(post("/api/v1/orders/" + order + "/cancel").with(customer("customer-b")))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/orders/" + order).with(admin())).andExpect(status().isOk());

    assertThat(remaining(product)).isEqualTo(2);
    mvc.perform(post("/api/v1/orders/" + order + "/cancel").with(customer("customer-a")))
        .andExpect(status().isNoContent());
    assertThat(remaining(product)).isEqualTo(3);
  }

  @Test
  void replayReturnsSameOrderWithoutConsumingMoreStock() throws Exception {
    UUID product = seed("SEC-101", 4);
    var body = orderJson(product, 2);

    var first =
        mvc.perform(
                post("/api/v1/orders")
                    .with(customer("customer-a"))
                    .header("Idempotency-Key", "order-key-0002")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isCreated())
            .andReturn();
    String location = first.getResponse().getHeader("Location");

    mvc.perform(
            post("/api/v1/orders")
                .with(customer("customer-a"))
                .header("Idempotency-Key", "order-key-0002")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.id")
                .value(
                    UUID.fromString(location.substring(location.lastIndexOf('/') + 1)).toString()));

    assertThat(remaining(product)).isEqualTo(2);
    assertThat(count("customer_orders")).isEqualTo(1);
    assertThat(count("order_idempotency")).isEqualTo(1);
    assertThat(count("stock_movements")).isEqualTo(1);
  }

  @Test
  void reusingKeyWithDifferentBodyIsConflictAndOtherCustomersHaveSeparateNamespaces()
      throws Exception {
    UUID product = seed("SEC-102", 5);
    createOrder(product, "customer-a", "order-key-0003", 1);
    mvc.perform(
            post("/api/v1/orders")
                .with(customer("customer-a"))
                .header("Idempotency-Key", "order-key-0003")
                .contentType(MediaType.APPLICATION_JSON)
                .content(orderJson(product, 2)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));

    createOrder(product, "customer-b", "order-key-0003", 1);
    assertThat(count("customer_orders")).isEqualTo(2);
    assertThat(remaining(product)).isEqualTo(3);
  }

  @Test
  void concurrentRetriesWithSameKeyProduceOnlyOneOrder() throws Exception {
    UUID product = seed("SEC-103", 2);
    var request = new CreateOrderRequest(List.of(new CreateOrderRequest.Item(product, 1)));
    var ready = new CountDownLatch(2);
    var start = new CountDownLatch(1);

    try (var pool = Executors.newFixedThreadPool(2)) {
      var one =
          pool.submit(
              () -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new AssertionError("Not started");
                return orders.create(request, "customer-a", "concurrent-key-001");
              });
      var two =
          pool.submit(
              () -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new AssertionError("Not started");
                return orders.create(request, "customer-a", "concurrent-key-001");
              });
      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      var left = one.get(30, TimeUnit.SECONDS);
      var right = two.get(30, TimeUnit.SECONDS);
      assertThat(left.order().id()).isEqualTo(right.order().id());
      assertThat(left.created()).isNotEqualTo(right.created());
    }
    assertThat(remaining(product)).isEqualTo(1);
    assertThat(count("customer_orders")).isEqualTo(1);
    assertThat(count("order_idempotency")).isEqualTo(1);
    assertThat(count("stock_movements")).isEqualTo(1);
  }

  @Test
  void aRolledBackAttemptDoesNotPermanentlyReserveTheIdempotencyKey() throws Exception {
    UUID product = seed("SEC-105", 1);
    String key = "retry-after-stock-01";
    mvc.perform(
            post("/api/v1/orders")
                .with(customer("customer-a"))
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(orderJson(product, 2)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));

    assertThat(count("order_idempotency")).isZero();
    jdbc.update("UPDATE inventory SET available_quantity = 3 WHERE product_id = ?", product);

    createOrder(product, "customer-a", key, 2);
    assertThat(count("order_idempotency")).isEqualTo(1);
    assertThat(count("customer_orders")).isEqualTo(1);
    assertThat(remaining(product)).isEqualTo(1);
  }

  @Test
  void requiresWellFormedIdempotencyKey() throws Exception {
    UUID product = seed("SEC-104", 2);
    mvc.perform(
            post("/api/v1/orders")
                .with(customer("customer-a"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(orderJson(product, 1)))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/v1/orders")
                .with(customer("customer-a"))
                .header("Idempotency-Key", "short")
                .contentType(MediaType.APPLICATION_JSON)
                .content(orderJson(product, 1)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_IDEMPOTENCY_KEY"));
    assertThat(count("customer_orders")).isZero();
  }

  @Test
  void convertsOnlyExplicitKeycloakRealmRoles() {
    Jwt good =
        Jwt.withTokenValue("sample")
            .header("alg", "RS256")
            .subject("customer-a")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(300))
            .claim("realm_access", Map.of("roles", List.of("CUSTOMER", "unknown", "ADMIN")))
            .build();

    var granted =
        converter.convert(good).getAuthorities().stream()
            .map(authority -> authority.getAuthority())
            .toList();
    assertThat(granted).contains("ROLE_CUSTOMER", "ROLE_ADMIN").doesNotContain("ROLE_unknown");

    Jwt noRoles =
        Jwt.withTokenValue("sample")
            .header("alg", "RS256")
            .subject("customer-a")
            .claim("scope", "ADMIN")
            .build();
    var noGranted =
        converter.convert(noRoles).getAuthorities().stream()
            .map(authority -> authority.getAuthority())
            .toList();
    assertThat(noGranted).doesNotContain("ROLE_ADMIN");
  }

  private UUID seed(String sku, int stock) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO products(id,sku,name,price,active,created_at,updated_at)
        VALUES (?, ?, ?, 10.00, true, now(), now())
        """,
        id,
        sku,
        sku);
    jdbc.update(
        "INSERT INTO inventory(product_id,available_quantity,updated_at) VALUES (?,?,now())",
        id,
        stock);
    return id;
  }

  private UUID createOrder(UUID product, String customer, String key, int quantity)
      throws Exception {
    var response =
        mvc.perform(
                post("/api/v1/orders")
                    .with(customer(customer))
                    .header("Idempotency-Key", key)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(orderJson(product, quantity)))
            .andExpect(status().isCreated())
            .andReturn();
    String location = response.getResponse().getHeader("Location");
    return UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
  }

  private int remaining(UUID product) {
    return jdbc.queryForObject(
        "SELECT available_quantity FROM inventory WHERE product_id = ?", Integer.class, product);
  }

  private long count(String table) {
    return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
  }

  private static String orderJson(UUID id, int quantity) {
    return """
        {"items":[{"productId":"%s","quantity":%d}]}
        """
        .formatted(id, quantity);
  }

  private static org.springframework.test.web.servlet.request.RequestPostProcessor customer(
      String subject) {
    return jwt()
        .jwt(builder -> builder.subject(subject))
        .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
  }

  private static org.springframework.test.web.servlet.request.RequestPostProcessor admin() {
    return jwt()
        .jwt(builder -> builder.subject("admin-a"))
        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
  }
}
