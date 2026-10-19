package io.github.josepauloferreira.orderflow.inventory.domain;

import java.util.UUID;

public class InactiveProductException extends RuntimeException {

  public InactiveProductException(UUID productId) {
    super("Product " + productId + " is inactive");
  }
}
