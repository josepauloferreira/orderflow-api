package io.github.josepauloferreira.orderflow.ordering.infrastructure;

import io.github.josepauloferreira.orderflow.ordering.application.IdempotencyConflictException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class OrderIdempotencyRepository {

  private final JdbcTemplate jdbc;

  /**
   * Acquires a unique database key before any product locks. PostgreSQL waits for concurrent
   * inserts with the same key to commit or roll back before resolving ON CONFLICT. The SELECT FOR
   * UPDATE then sees the completed request.
   */
  public UUID claim(String customer, String key, String digest) {
    int inserted =
        jdbc.update(
            """
            INSERT INTO order_idempotency(customer_subject, idempotency_key, request_digest)
            VALUES (?, ?, ?) ON CONFLICT (customer_subject, idempotency_key) DO NOTHING
            """,
            customer,
            key,
            digest);
    if (inserted == 1) {
      return null;
    }
    return jdbc.queryForObject(
        """
        SELECT request_digest, order_id FROM order_idempotency
        WHERE customer_subject = ? AND idempotency_key = ? FOR UPDATE
        """,
        (rs, rowNumber) -> {
          if (!digest.equals(rs.getString("request_digest"))) {
            throw new IdempotencyConflictException();
          }
          UUID orderId = rs.getObject("order_id", UUID.class);
          if (orderId == null) {
            throw new IllegalStateException("Incomplete committed idempotency record");
          }
          return orderId;
        },
        customer,
        key);
  }

  public void complete(String customer, String key, UUID orderId) {
    int updated =
        jdbc.update(
            """
            UPDATE order_idempotency SET order_id = ?
            WHERE customer_subject = ? AND idempotency_key = ? AND order_id IS NULL
            """,
            orderId,
            customer,
            key);
    if (updated != 1) {
      throw new IllegalStateException("Cannot finalize idempotency record");
    }
  }
}
