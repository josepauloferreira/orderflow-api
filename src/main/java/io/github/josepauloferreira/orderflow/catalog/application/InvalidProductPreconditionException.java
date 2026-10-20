package io.github.josepauloferreira.orderflow.catalog.application;

public class InvalidProductPreconditionException extends RuntimeException {
  public InvalidProductPreconditionException() {
    super("If-Match must contain exactly one strong numeric version ETag");
  }
}
