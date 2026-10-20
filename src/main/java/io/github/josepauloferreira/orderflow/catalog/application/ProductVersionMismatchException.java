package io.github.josepauloferreira.orderflow.catalog.application;

public class ProductVersionMismatchException extends RuntimeException {
  public ProductVersionMismatchException() {
    super("Product has changed; read its latest ETag and retry with a fresh version");
  }
}
