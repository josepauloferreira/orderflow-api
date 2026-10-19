package io.github.josepauloferreira.orderflow.common.api;

import io.github.josepauloferreira.orderflow.catalog.domain.ProductNotFoundException;
import io.github.josepauloferreira.orderflow.inventory.api.StockController;
import io.github.josepauloferreira.orderflow.inventory.domain.InactiveProductException;
import io.github.josepauloferreira.orderflow.inventory.domain.InsufficientStockException;
import io.github.josepauloferreira.orderflow.inventory.domain.InvalidStockAdjustmentException;
import io.github.josepauloferreira.orderflow.ordering.api.OrderController;
import io.github.josepauloferreira.orderflow.ordering.domain.InvalidOrderException;
import io.github.josepauloferreira.orderflow.ordering.domain.OrderNotFoundException;
import java.util.Comparator;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {StockController.class, OrderController.class})
public class InventoryOrderingExceptionHandler {

  @ExceptionHandler(ProductNotFoundException.class)
  public ProblemDetail productNotFound(ProductNotFoundException failure) {
    return problem(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", failure.getMessage());
  }

  @ExceptionHandler(OrderNotFoundException.class)
  public ProblemDetail orderNotFound(OrderNotFoundException failure) {
    return problem(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", failure.getMessage());
  }

  @ExceptionHandler(InsufficientStockException.class)
  public ProblemDetail insufficientStock(InsufficientStockException failure) {
    return problem(HttpStatus.CONFLICT, "INSUFFICIENT_STOCK", failure.getMessage());
  }

  @ExceptionHandler(InactiveProductException.class)
  public ProblemDetail inactiveProduct(InactiveProductException failure) {
    return problem(HttpStatus.CONFLICT, "PRODUCT_INACTIVE", failure.getMessage());
  }

  @ExceptionHandler({InvalidStockAdjustmentException.class, InvalidOrderException.class})
  public ProblemDetail invalidOperation(RuntimeException failure) {
    return problem(HttpStatus.BAD_REQUEST, "INVALID_OPERATION", failure.getMessage());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ProblemDetail validationFailure(MethodArgumentNotValidException failure) {
    var detail = problem(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed");
    detail.setProperty(
        "violations",
        failure.getBindingResult().getFieldErrors().stream()
            .map(
                error ->
                    Map.of(
                        "field",
                        error.getField(),
                        "message",
                        error.getDefaultMessage() == null
                            ? "Invalid value"
                            : error.getDefaultMessage()))
            .sorted(Comparator.comparing(value -> value.get("field")))
            .toList());
    return detail;
  }

  private static ProblemDetail problem(HttpStatus status, String code, String detail) {
    var problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setProperty("code", code);
    return problem;
  }
}
