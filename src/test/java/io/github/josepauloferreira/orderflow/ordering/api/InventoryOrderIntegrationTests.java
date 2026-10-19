package io.github.josepauloferreira.orderflow.ordering.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.josepauloferreira.orderflow.inventory.api.dto.StockAdjustmentRequest;
import io.github.josepauloferreira.orderflow.inventory.application.StockService;
import io.github.josepauloferreira.orderflow.inventory.domain.InsufficientStockException;
import io.github.josepauloferreira.orderflow.ordering.api.dto.CreateOrderRequest;
import io.github.josepauloferreira.orderflow.ordering.application.OrderService;
import jakarta.persistence.EntityManager;
import java.util.List;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@WithMockUser(roles = {"ADMIN", "CUSTOMER"})
class InventoryOrderIntegrationTests {

  @Container @ServiceConnection
  static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private OrderService orders;
  @Autowired private StockService stockService;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private EntityManager entityManager;

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
  void newProductHasNoStockUntilAdjustedAndMovementIsRecorded() throws Exception {
    UUID product = createProduct("STOCK-1", "10.00");

    mvc.perform(get(stockUrl(product)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.availableQuantity").value(0));

    adjust(product, 4, "Initial delivery");
    mvc.perform(get(stockUrl(product)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.availableQuantity").value(4));

    assertThat(jdbc.queryForObject("SELECT count(*) FROM stock_movements", Long.class))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT available_quantity FROM inventory WHERE product_id = ?",
                Integer.class,
                product))
        .isEqualTo(4);
  }

  @Test
  void insufficientManualAdjustmentDoesNotCreateAnAuditEntry() throws Exception {
    UUID product = createProduct("STOCK-2", "10.00");
    adjust(product, 2, "Initial delivery");

    mvc.perform(
            post(stockUrl(product) + "/adjustments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"delta":-3,"reason":"Damage"}
                    """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));

    assertThat(available(product)).isEqualTo(2);
    assertThat(count("stock_movements")).isEqualTo(1);
  }

  @Test
  void orderCreationAndCancellationPreserveStockAndPriceSnapshots() throws Exception {
    UUID first = createProduct("STOCK-3", "15.00");
    UUID second = createProduct("STOCK-4", "7.50");
    adjust(first, 5, "Initial delivery");
    adjust(second, 1, "Initial delivery");

    UUID order = createOrder(first, 2, second, 1);
    mvc.perform(get("/api/v1/orders/" + order))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CONFIRMED"))
        .andExpect(jsonPath("$.totalAmount").value(37.5))
        .andExpect(jsonPath("$.items.length()").value(2));

    assertThat(available(first)).isEqualTo(3);
    assertThat(available(second)).isZero();

    // Historical prices are independent from later catalog edits.
    mvc.perform(
            put("/api/v1/products/" + first)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"New name","description":null,"price":199.00}
                    """))
        .andExpect(status().isOk());
    mvc.perform(get("/api/v1/orders/" + order))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalAmount").value(37.5));

    mvc.perform(post("/api/v1/orders/" + order + "/cancel")).andExpect(status().isNoContent());
    mvc.perform(post("/api/v1/orders/" + order + "/cancel")).andExpect(status().isNoContent());

    assertThat(available(first)).isEqualTo(5);
    assertThat(available(second)).isEqualTo(1);
    assertThat(count("stock_movements")).isEqualTo(6);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM stock_movements WHERE kind = 'ORDER_CANCELLED'", Long.class))
        .isEqualTo(2);
    mvc.perform(get("/api/v1/orders/" + order)).andExpect(jsonPath("$.status").value("CANCELLED"));
  }

  @Test
  void partialFailureRollsBackEveryOrderChange() throws Exception {
    UUID first = createProduct("STOCK-5", "10.00");
    UUID second = createProduct("STOCK-6", "10.00");
    adjust(first, 2, "Initial delivery");
    adjust(second, 1, "Initial delivery");

    mvc.perform(
            post("/api/v1/orders").header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(orderJson(first, 2, second, 2)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));

    assertThat(available(first)).isEqualTo(2);
    assertThat(available(second)).isEqualTo(1);
    assertThat(count("customer_orders")).isZero();
    assertThat(count("order_items")).isZero();
    assertThat(count("stock_movements")).isEqualTo(2);
  }

  @Test
  void duplicateProductsAndInvalidQuantitiesAreRejected() throws Exception {
    UUID product = createProduct("STOCK-7", "10.00");
    adjust(product, 5, "Initial delivery");

    mvc.perform(
            post("/api/v1/orders").header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(orderJson(product, 1, product, 1)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_OPERATION"));

    mvc.perform(
            post("/api/v1/orders").header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(orderJson(product, 0, product, 1)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    assertThat(count("customer_orders")).isZero();
  }

  @Test
  void inactiveProductCannotBeOrdered() throws Exception {
    UUID product = createProduct("STOCK-8", "10.00");
    adjust(product, 1, "Initial delivery");
    mvc.perform(post("/api/v1/products/" + product + "/deactivate"))
        .andExpect(status().isNoContent());

    mvc.perform(
            post("/api/v1/orders").header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(orderJson(product, 1)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("PRODUCT_INACTIVE"));
    assertThat(available(product)).isEqualTo(1);
  }

  @Test
  void concurrentOrdersCompetingForLastUnitNeverOversell() throws Exception {
    UUID product = createProduct("STOCK-9", "20.00");
    adjust(product, 1, "Initial delivery");
    var request = new CreateOrderRequest(List.of(new CreateOrderRequest.Item(product, 1)));
    var start = new CountDownLatch(1);
    var ready = new CountDownLatch(2);

    try (var pool = Executors.newFixedThreadPool(2)) {
      var first = pool.submit(() -> placeConcurrentOrder(request, ready, start));
      var second = pool.submit(() -> placeConcurrentOrder(request, ready, start));
      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      start.countDown();

      var results = List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
      assertThat(results).containsExactlyInAnyOrder("CONFIRMED", "INSUFFICIENT_STOCK");
    }

    assertThat(available(product)).isZero();
    assertThat(count("customer_orders")).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM stock_movements WHERE kind = 'ORDER_CONFIRMED'", Long.class))
        .isEqualTo(1);
  }

  @Test
  void failureAfterFlushingStockAndLedgerRollsBackTheEntireTransaction() throws Exception {
    UUID product = createProduct("STOCK-11", "25.00");
    adjust(product, 2, "Initial delivery");

    assertThatThrownBy(
            () ->
                transactionTemplate.executeWithoutResult(
                    status -> {
                      stockService.adjust(
                          product, new StockAdjustmentRequest(3, "Receipt before failure"));
                      entityManager.flush();
                      jdbc.update(
                          "UPDATE inventory SET available_quantity = -1 WHERE product_id = ?",
                          product);
                    }))
        .isInstanceOf(DataIntegrityViolationException.class);

    assertThat(available(product)).isEqualTo(2);
    assertThat(count("stock_movements")).isEqualTo(1);
  }

  @Test
  void parallelFirstStockAdjustmentsCreateOnlyOneInventoryRow() throws Exception {
    UUID product = createProduct("STOCK-12", "25.00");
    var start = new CountDownLatch(1);
    var ready = new CountDownLatch(2);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var first =
          pool.submit(
              () -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) {
                  throw new AssertionError("First adjustment did not start");
                }
                stockService.adjust(product, new StockAdjustmentRequest(1, "Receipt A"));
                return true;
              });
      var second =
          pool.submit(
              () -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) {
                  throw new AssertionError("Second adjustment did not start");
                }
                stockService.adjust(product, new StockAdjustmentRequest(2, "Receipt B"));
                return true;
              });

      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      assertThat(first.get(30, TimeUnit.SECONDS)).isTrue();
      assertThat(second.get(30, TimeUnit.SECONDS)).isTrue();
    }

    assertThat(available(product)).isEqualTo(3);
    assertThat(count("inventory")).isEqualTo(1);
    assertThat(count("stock_movements")).isEqualTo(2);
  }

  @Test
  void directSqlCannotMakeStockNegative() throws Exception {
    UUID product = createProduct("STOCK-10", "20.00");
    adjust(product, 1, "Initial delivery");

    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE inventory SET available_quantity = -1 WHERE product_id = ?", product))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThat(available(product)).isEqualTo(1);
  }

  private String placeConcurrentOrder(
      CreateOrderRequest request, CountDownLatch ready, CountDownLatch start) throws Exception {
    ready.countDown();
    if (!start.await(10, TimeUnit.SECONDS)) {
      throw new AssertionError("Concurrent test did not start");
    }
    try {
      orders.create(request, "user", UUID.randomUUID().toString());
      return "CONFIRMED";
    } catch (InsufficientStockException expected) {
      return "INSUFFICIENT_STOCK";
    }
  }

  private UUID createProduct(String sku, String price) throws Exception {
    var response =
        mvc.perform(
                post("/api/v1/products")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"sku":"%s","name":"Product %s","price":%s}
                        """
                            .formatted(sku, sku, price)))
            .andExpect(status().isCreated())
            .andReturn();
    return UUID.fromString(response.getResponse().getHeader("Location").split("/")[4]);
  }

  private void adjust(UUID product, int delta, String reason) throws Exception {
    mvc.perform(
            post(stockUrl(product) + "/adjustments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"delta":%d,"reason":"%s"}
                    """
                        .formatted(delta, reason)))
        .andExpect(status().isOk());
  }

  private UUID createOrder(UUID first, int qtyFirst, UUID second, int qtySecond) throws Exception {
    var response =
        mvc.perform(
                post("/api/v1/orders").header("Idempotency-Key", UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(orderJson(first, qtyFirst, second, qtySecond)))
            .andExpect(status().isCreated())
            .andReturn();
    return UUID.fromString(response.getResponse().getHeader("Location").split("/")[4]);
  }

  private int available(UUID product) {
    return jdbc.queryForObject(
        "SELECT available_quantity FROM inventory WHERE product_id = ?", Integer.class, product);
  }

  private long count(String table) {
    return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
  }

  private static String stockUrl(UUID product) {
    return "/api/v1/products/" + product + "/stock";
  }

  private static String orderJson(UUID first, int firstQuantity) {
    return """
        {"items":[{"productId":"%s","quantity":%d}]}
        """
        .formatted(first, firstQuantity);
  }

  private static String orderJson(UUID first, int firstQuantity, UUID second, int secondQuantity) {
    return """
        {"items":[{"productId":"%s","quantity":%d},{"productId":"%s","quantity":%d}]}
        """
        .formatted(first, firstQuantity, second, secondQuantity);
  }
}
