package io.github.josepauloferreira.orderflow.ordering.application;

public class InvalidIdempotencyKeyException extends RuntimeException {

  public InvalidIdempotencyKeyException() {
    super("Idempotency-Key must be 8 to 128 ASCII letters, numbers or . _ : -");
  }
}
