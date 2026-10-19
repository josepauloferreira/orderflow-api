package io.github.josepauloferreira.orderflow.catalog.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.UUID;
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
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
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

    mockMvc
        .perform(get(uri))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sku").value("AB-123"));
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
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"Updated Keyboard","description":null,"price":119.90}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sku").value("SKU-987"))
        .andExpect(jsonPath("$.name").value("Updated Keyboard"))
        .andExpect(jsonPath("$.description").doesNotExist());

    mockMvc.perform(post(uri + "/deactivate")).andExpect(status().isNoContent());
    mockMvc.perform(post(uri + "/deactivate")).andExpect(status().isNoContent());
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
        .perform(post(created.getResponse().getHeader("Location") + "/deactivate"))
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
