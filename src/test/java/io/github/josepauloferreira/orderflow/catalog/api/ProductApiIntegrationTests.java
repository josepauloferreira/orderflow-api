package io.github.josepauloferreira.orderflow.catalog.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.List;
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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@WithMockUser(roles = "ADMIN")
class ProductApiIntegrationTests {

  private static final String ENDPOINT = "/api/v1/products";

  @Container @ServiceConnection
  static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void cleanDatabase() {
    jdbc.update("DELETE FROM products");
  }

  @Test
  void createsAndRetrievesProductWithCanonicalSku() throws Exception {
    var result =
        mockMvc
            .perform(
                post(ENDPOINT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createBody(" ab-123 ")))
            .andExpect(status().isCreated())
            .andExpect(header().exists("Location"))
            .andExpect(jsonPath("$.sku").value("AB-123"))
            .andExpect(jsonPath("$.name").value("Mechanical Keyboard"))
            .andExpect(jsonPath("$.active").value(true))
            .andReturn();

    var uri = result.getResponse().getHeader("Location");
    assertThat(uri).startsWith(ENDPOINT + "/");
    assertThat(result.getResponse().getHeader("ETag")).isEqualTo("\"0\"");

    mockMvc
        .perform(get(uri))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sku").value("AB-123"))
        .andExpect(header().string("ETag", "\"0\""));
    assertThat(jdbc.queryForObject("SELECT count(*) FROM products", Long.class)).isEqualTo(1);
  }

  @Test
  void rejectsDuplicateSkuIncludingMixedCase() throws Exception {
    mockMvc
        .perform(
            post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(createBody("abc-123")))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(createBody("ABC-123")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("DUPLICATE_SKU"))
        .andExpect(jsonPath("$.status").value(409));
  }

  @Test
  void rejectsInvalidInputWithFieldViolations() throws Exception {
    mockMvc
        .perform(
            post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"sku":"x","name":"   ","price":-1}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.violations").isArray());

    assertThat(jdbc.queryForObject("SELECT count(*) FROM products", Long.class)).isZero();
  }

  @Test
  void updatesAndDeactivatesWithoutChangingSku() throws Exception {
    var created =
        mockMvc
            .perform(
                post(ENDPOINT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createBody("sku-987")))
            .andExpect(status().isCreated())
            .andReturn();
    var uri = created.getResponse().getHeader("Location");

    mockMvc
        .perform(
            put(uri)
                .header("If-Match", "\"0\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"Updated Keyboard","description":null,"price":119.90}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sku").value("SKU-987"))
        .andExpect(jsonPath("$.name").value("Updated Keyboard"))
        .andExpect(jsonPath("$.description").doesNotExist())
        .andExpect(header().string("ETag", "\"1\""));

    mockMvc.perform(post(uri + "/deactivate").header("If-Match", "\"1\""))
        .andExpect(status().isNoContent())
        .andExpect(header().string("ETag", "\"2\""));
    mockMvc.perform(post(uri + "/deactivate").header("If-Match", "\"2\""))
        .andExpect(status().isNoContent())
        .andExpect(header().string("ETag", "\"2\""));
    mockMvc
        .perform(get(uri))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.active").value(false));
  }

  @Test
  void pagesAndFiltersUsingStableResponseShape() throws Exception {
    var created =
        mockMvc
            .perform(
                post(ENDPOINT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createBody("sku-001")))
            .andExpect(status().isCreated())
            .andReturn();
    mockMvc
        .perform(
            post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(createBody("sku-002")))
        .andExpect(status().isCreated());

    mockMvc
        .perform(post(created.getResponse().getHeader("Location") + "/deactivate")
                .header("If-Match", "\"0\""))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(get(ENDPOINT).param("active", "true").param("page", "0").param("size", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].sku").value("SKU-002"));

    mockMvc
        .perform(get(ENDPOINT).param("page", "-1"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_PAGINATION"));
  }

  @Test
  void returnsNotFoundForUnknownProduct() throws Exception {
    mockMvc
        .perform(get(ENDPOINT + "/" + UUID.randomUUID()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
  }

  @Test
  void conditionalWritesRejectMissingMalformedAndStaleEtags() throws Exception {
    var created = mockMvc.perform(post(ENDPOINT)
            .contentType(MediaType.APPLICATION_JSON)
            .content(createBody("CAS-001")))
        .andExpect(status().isCreated())
        .andReturn();
    String uri = created.getResponse().getHeader("Location");
    String updateBody = """
        {"name":"Safe update","description":null,"price":90.00}
        """;

    mockMvc.perform(put(uri).contentType(MediaType.APPLICATION_JSON).content(updateBody))
        .andExpect(status().isPreconditionRequired())
        .andExpect(jsonPath("$.code").value("PRECONDITION_REQUIRED"));
    mockMvc.perform(put(uri).header("If-Match", "W/\"0\"")
            .contentType(MediaType.APPLICATION_JSON).content(updateBody))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_IF_MATCH"));

    mockMvc.perform(put(uri).header("If-Match", "\"0\"")
            .contentType(MediaType.APPLICATION_JSON).content(updateBody))
        .andExpect(status().isOk())
        .andExpect(header().string("ETag", "\"1\""));

    mockMvc.perform(put(uri).header("If-Match", "\"0\"")
            .contentType(MediaType.APPLICATION_JSON).content("""
                {"name":"Lost update","description":null,"price":50.00}
                """))
        .andExpect(status().isPreconditionFailed())
        .andExpect(jsonPath("$.code").value("STALE_PRODUCT_VERSION"));

    mockMvc.perform(get(uri)).andExpect(status().isOk())
        .andExpect(header().string("ETag", "\"1\""))
        .andExpect(jsonPath("$.name").value("Safe update"));
    mockMvc.perform(post(uri + "/deactivate").header("If-Match", "\"0\""))
        .andExpect(status().isPreconditionFailed())
        .andExpect(jsonPath("$.code").value("STALE_PRODUCT_VERSION"));
  }

  @Test
  void simultaneousConditionalEditsCannotBothWin() throws Exception {
    var created = mockMvc.perform(post(ENDPOINT)
            .contentType(MediaType.APPLICATION_JSON)
            .content(createBody("CAS-002")))
        .andExpect(status().isCreated())
        .andReturn();
    String uri = created.getResponse().getHeader("Location");
    var ready = new CountDownLatch(2);
    var start = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var first = pool.submit(() -> conditionalEdit(uri, "First writer", ready, start));
      var second = pool.submit(() -> conditionalEdit(uri, "Second writer", ready, start));
      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      assertThat(List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(200, 412);
    }

    mockMvc.perform(get(uri)).andExpect(status().isOk())
        .andExpect(header().string("ETag", "\"1\""));
    assertThat(jdbc.queryForObject(
            "SELECT version FROM products WHERE id = ?",
            Long.class, UUID.fromString(uri.substring(uri.lastIndexOf('/') + 1))))
        .isEqualTo(1);
  }

  @Test
  void frameworkFailuresUseTheProblemDetailsContract() throws Exception {
    mockMvc.perform(get(ENDPOINT + "/not-a-uuid"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    mockMvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content("{"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("MALFORMED_JSON"));
  }

  private int conditionalEdit(
      String uri, String name, CountDownLatch ready, CountDownLatch start) throws Exception {
    ready.countDown();
    if (!start.await(10, TimeUnit.SECONDS)) {
      throw new AssertionError("Concurrent editing did not start");
    }
    return mockMvc.perform(put(uri)
            .with(user("admin").roles("ADMIN"))
            .header("If-Match", "\"0\"")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"name":"%s","description":null,"price":100.00}
                """.formatted(name)))
        .andReturn().getResponse().getStatus();
  }

  @Test
  void databaseEnforcesPriceAndSkuConstraintsIndependentlyOfJava() {
    assertThatThrownBy(
            () ->
                jdbc.update(
                    """
                    INSERT INTO products
                      (id, sku, name, price, active, created_at, updated_at)
                    VALUES (?, ?, ?, ?, true, now(), now())
                    """,
                    UUID.randomUUID(),
                    "VALID-01",
                    "Product",
                    new BigDecimal("-1.00")))
        .isInstanceOf(DataIntegrityViolationException.class);

    assertThatThrownBy(
            () ->
                jdbc.update(
                    """
                    INSERT INTO products
                      (id, sku, name, price, active, created_at, updated_at)
                    VALUES (?, ?, ?, ?, true, now(), now())
                    """,
                    UUID.randomUUID(),
                    "lowercase",
                    "Product",
                    new BigDecimal("10.00")))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private static String createBody(String sku) {
    return """
        {"sku":"%s","name":"Mechanical Keyboard","description":"Quiet switches","price":129.90}
        """
        .formatted(sku);
  }
}
