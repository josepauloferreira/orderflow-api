package io.github.josepauloferreira.orderflow.common.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class OperationsIntegrationTests {

  @Container @ServiceConnection
  static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

  @Autowired private MockMvc mvc;
  @Autowired private MeterRegistry registry;

  @Test
  void livenessAndReadinessArePublicAndCorrelated() throws Exception {
    mvc.perform(get("/actuator/health/liveness"))
        .andExpect(status().isOk())
        .andExpect(header().exists("X-Request-ID"));

    mvc.perform(get("/actuator/health/readiness"))
        .andExpect(status().isOk())
        .andExpect(header().exists("X-Request-ID"));
  }

  @Test
  void metricsAreVisibleOnlyToAdministrators() throws Exception {
    mvc.perform(get("/actuator/prometheus")).andExpect(status().isUnauthorized());

    mvc.perform(
            get("/actuator/prometheus")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
        .andExpect(status().isForbidden());

    var result =
        mvc.perform(
                get("/actuator/prometheus")
                    .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
            .andExpect(status().isOk())
            .andReturn();

    assertThat(result.getResponse().getContentAsString())
        .contains("orderflow_orders_created_total");
    assertThat(registry.find("orderflow.orders.created").counter()).isNotNull();
  }

  @Test
  void unsafeIncomingRequestIdsAreReplacedWithoutReflectingHeaderInjection() throws Exception {
    mvc.perform(get("/api/v1/products").header("X-Request-ID", "safe-request-42"))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Request-ID", "safe-request-42"));

    var response =
        mvc.perform(get("/api/v1/products").header("X-Request-ID", "not safe!"))
            .andExpect(status().isOk())
            .andReturn();
    String generated = response.getResponse().getHeader("X-Request-ID");
    assertThat(generated).isNotEqualTo("not safe!");
    assertThat(UUID.fromString(generated)).isNotNull();
  }
}
