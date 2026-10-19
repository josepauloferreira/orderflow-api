package io.github.josepauloferreira.orderflow.ordering.application;

public class IdempotencyConflictException extends RuntimeException {

  public IdempotencyConflictException() {
    super("Idempotency-Key was already used for a different order request");
  }
}
