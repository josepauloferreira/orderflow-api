package io.github.josepauloferreira.orderflow.common.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Counts only committed business changes. No user IDs, SKUs, or order UUIDs appear as labels,
 * avoiding high-cardinality time series.
 */
@Component
public class DomainMetrics {

  private final Counter ordersCreated;
  private final Counter ordersReplayed;
  private final Counter ordersCancelled;
  private final Counter stockAdjusted;

  public DomainMetrics(MeterRegistry registry) {
    ordersCreated = registry.counter("orderflow.orders.created");
    ordersReplayed = registry.counter("orderflow.orders.replayed");
    ordersCancelled = registry.counter("orderflow.orders.cancelled");
    stockAdjusted = registry.counter("orderflow.stock.adjusted");
  }

  public void orderCreated() {
    incrementAfterCommit(ordersCreated);
  }

  public void orderReplayed() {
    incrementAfterCommit(ordersReplayed);
  }

  public void orderCancelled() {
    incrementAfterCommit(ordersCancelled);
  }

  public void stockAdjusted() {
    incrementAfterCommit(stockAdjusted);
  }

  private static void incrementAfterCommit(Counter counter) {
    if (!TransactionSynchronizationManager.isActualTransactionActive()
        || !TransactionSynchronizationManager.isSynchronizationActive()) {
      throw new IllegalStateException("Domain metric must be registered inside a transaction");
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            counter.increment();
          }
        });
  }
}
