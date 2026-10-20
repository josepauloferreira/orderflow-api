package io.github.josepauloferreira.orderflow.common.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class OpenApiContractIntegrationTests {

  @Container @ServiceConnection
  static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper mapper;

  @Test
  void generatedSpecificationDocumentsTheImplementedEndpointsAndRoles() throws Exception {
    String json =
        mvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    var doc = mapper.readTree(json);
    assertThat(doc.path("info").path("title").asText()).isEqualTo("OrderFlow API");
    assertThat(doc.path("info").path("version").asText()).isEqualTo("v1");

    var paths = doc.path("paths");
    assertThat(paths.path("/api/v1/products").has("post")).isTrue();
    assertThat(paths.path("/api/v1/products").has("get")).isTrue();
    assertThat(paths.path("/api/v1/products/{id}").has("put")).isTrue();
    assertThat(paths.path("/api/v1/products/{id}/deactivate").has("post")).isTrue();
    assertThat(paths.path("/api/v1/products/{productId}/stock").has("get")).isTrue();
    assertThat(paths.path("/api/v1/products/{productId}/stock/adjustments").has("post")).isTrue();
    assertThat(paths.path("/api/v1/orders").has("post")).isTrue();
    assertThat(paths.path("/api/v1/orders/{id}").has("get")).isTrue();
    assertThat(paths.path("/api/v1/orders/{id}/cancel").has("post")).isTrue();

    assertThat(doc.path("components").path("securitySchemes").path("bearerAuth").path("scheme").asText())
        .isEqualTo("bearer");

    assertThat(paths.path("/api/v1/orders").path("post").path("security").toString())
        .contains("bearerAuth");
    assertThat(paths.path("/api/v1/products").path("post").path("security").toString())
        .contains("bearerAuth");
    assertThat(paths.path("/api/v1/products").path("get").path("security").isEmpty())
        .isTrue();

    assertThat(paths.path("/api/v1/orders").path("post").path("parameters").toString())
        .contains("Idempotency-Key");
    assertThat(paths.path("/api/v1/orders").path("post").path("responses").has("201")).isTrue();
    assertThat(paths.path("/api/v1/orders").path("post").path("responses").has("200")).isTrue();
  }

  @Test
  void swaggerUiIsAvailableInTheTestProfile() throws Exception {
    mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
  }
}
