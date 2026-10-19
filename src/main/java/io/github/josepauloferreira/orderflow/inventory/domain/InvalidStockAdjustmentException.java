package io.github.josepauloferreira.orderflow.inventory.domain;

public class InvalidStockAdjustmentException extends RuntimeException {

  public InvalidStockAdjustmentException(String message) {
    super(message);
  }
}
