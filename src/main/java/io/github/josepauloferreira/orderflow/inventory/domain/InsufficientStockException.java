package io.github.josepauloferreira.orderflow.inventory.domain;

import java.util.UUID;

public class InsufficientStockException extends RuntimeException {

  public InsufficientStockException(UUID productId, long available, long requested) {
    super(
        "Insufficient stock for product "
            + productId
            + ": available="
            + available
            + ", requested="
            + requested);
  }
}
