package io.github.josepauloferreira.orderflow.inventory.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "stock_movements")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockMovement {

  @Id private UUID id;

  private UUID productId;
  private UUID orderId;

  @Enumerated(EnumType.STRING)
  private MovementKind kind;

  private int delta;
  private String note;
  private Instant createdAt;

  public static StockMovement record(
      UUID productId, UUID orderId, MovementKind kind, int delta, String note) {
    var movement = new StockMovement();
    movement.id = UUID.randomUUID();
    movement.productId = productId;
    movement.orderId = orderId;
    movement.kind = kind;
    movement.delta = delta;
    movement.note = note;
    movement.createdAt = Instant.now();
    return movement;
  }
}
