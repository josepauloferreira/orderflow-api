package io.github.josepauloferreira.orderflow.catalog.domain;

import java.util.UUID;

public class ProductNotFoundException extends RuntimeException {
  public ProductNotFoundException(UUID id) {
    super("Product " + id + " was not found");
  }
}
