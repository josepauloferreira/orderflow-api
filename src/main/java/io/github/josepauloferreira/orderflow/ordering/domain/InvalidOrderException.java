package io.github.josepauloferreira.orderflow.ordering.domain;

public class InvalidOrderException extends RuntimeException {

  public InvalidOrderException(String message) {
    super(message);
  }
}
