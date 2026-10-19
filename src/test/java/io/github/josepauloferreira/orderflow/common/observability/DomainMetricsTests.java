package io.github.josepauloferreira.orderflow.common.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class DomainMetricsTests {

  @Test
  void requiresARealTransactionToScheduleDomainMetrics() {
    var registry = new SimpleMeterRegistry();
    var metrics = new DomainMetrics(registry);
    assertThatThrownBy(metrics::orderCreated).isInstanceOf(IllegalStateException.class);
    assertThat(registry.get("orderflow.orders.created").counter().count()).isZero();
  }

  @Test
  void countsOnlyCommittedBusinessChanges() {
    var registry = new SimpleMeterRegistry();
    var metrics = new DomainMetrics(registry);
    TransactionSynchronizationManager.initSynchronization();
    TransactionSynchronizationManager.setActualTransactionActive(true);
    try {
      metrics.orderCreated();
      assertThat(registry.get("orderflow.orders.created").counter().count()).isZero();
      TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCommit());
      assertThat(registry.get("orderflow.orders.created").counter().count()).isEqualTo(1);
    } finally {
      TransactionSynchronizationManager.clear();
    }
  }
}
