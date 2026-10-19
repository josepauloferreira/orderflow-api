package io.github.josepauloferreira.orderflow.inventory.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "inventory")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Stock {

  @Id private UUID productId;

  private int availableQuantity;
  private Instant updatedAt;

  public static Stock empty(UUID productId) {
    var stock = new Stock();
    stock.productId = productId;
    stock.availableQuantity = 0;
    stock.updatedAt = Instant.now();
    return stock;
  }

  public void change(int delta) {
    if (delta == 0) {
      throw new InvalidStockAdjustmentException("Stock change cannot be zero");
    }
    long next = (long) availableQuantity + delta;
    if (next < 0) {
      throw new InsufficientStockException(productId, availableQuantity, -((long) delta));
    }
    if (next > Integer.MAX_VALUE) {
      throw new InvalidStockAdjustmentException("Stock quantity would overflow");
    }
    availableQuantity = (int) next;
    updatedAt = Instant.now();
  }
}
