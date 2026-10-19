package io.github.josepauloferreira.orderflow.catalog.api;

import io.github.josepauloferreira.orderflow.catalog.application.InvalidPaginationException;
import io.github.josepauloferreira.orderflow.catalog.domain.DuplicateSkuException;
import io.github.josepauloferreira.orderflow.catalog.domain.ProductNotFoundException;
import java.util.Comparator;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = ProductController.class)
public class ProductApiExceptionHandler {

  @ExceptionHandler(ProductNotFoundException.class)
  public ProblemDetail handleNotFound(ProductNotFoundException exception) {
    return problem(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", exception.getMessage());
  }

  @ExceptionHandler(DuplicateSkuException.class)
  public ProblemDetail handleDuplicateSku(DuplicateSkuException exception) {
    return problem(HttpStatus.CONFLICT, "DUPLICATE_SKU", exception.getMessage());
  }

  @ExceptionHandler(InvalidPaginationException.class)
  public ProblemDetail handleInvalidPagination(InvalidPaginationException exception) {
    return problem(HttpStatus.BAD_REQUEST, "INVALID_PAGINATION", exception.getMessage());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ProblemDetail handleInvalidRequest(MethodArgumentNotValidException exception) {
    var problem = problem(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed");
    var violations =
        exception.getBindingResult().getFieldErrors().stream()
            .map(
                error ->
                    Map.of(
                        "field",
                        error.getField(),
                        "message",
                        error.getDefaultMessage() == null
                            ? "Invalid value"
                            : error.getDefaultMessage()))
            .sorted(Comparator.comparing(item -> item.get("field")))
            .toList();
    problem.setProperty("violations", violations);
    return problem;
  }

  private static ProblemDetail problem(HttpStatus status, String code, String detail) {
    var problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setProperty("code", code);
    return problem;
  }
}
