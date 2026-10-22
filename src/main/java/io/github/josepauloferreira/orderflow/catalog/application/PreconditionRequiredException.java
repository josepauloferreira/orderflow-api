package io.github.josepauloferreira.orderflow.catalog.application;

public class PreconditionRequiredException extends RuntimeException {
  public PreconditionRequiredException() {
    super("An If-Match header with the current strong product ETag is required");
  }
}
