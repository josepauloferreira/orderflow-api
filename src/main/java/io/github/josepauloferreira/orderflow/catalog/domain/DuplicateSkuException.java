package io.github.josepauloferreira.orderflow.catalog.domain;

public class DuplicateSkuException extends RuntimeException {
  public DuplicateSkuException(String sku) {
    super("A product with SKU " + sku + " already exists");
  }
}
