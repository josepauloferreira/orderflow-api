package io.github.josepauloferreira.orderflow.common.api;

import io.github.josepauloferreira.orderflow.catalog.application.InvalidPaginationException;
import io.github.josepauloferreira.orderflow.catalog.application.InvalidProductPreconditionException;
import io.github.josepauloferreira.orderflow.catalog.application.PreconditionRequiredException;
import io.github.josepauloferreira.orderflow.catalog.application.ProductVersionMismatchException;
import io.github.josepauloferreira.orderflow.catalog.domain.DuplicateSkuException;
import io.github.josepauloferreira.orderflow.catalog.domain.ProductNotFoundException;
import io.github.josepauloferreira.orderflow.inventory.domain.InactiveProductException;
import io.github.josepauloferreira.orderflow.inventory.domain.InsufficientStockException;
import io.github.josepauloferreira.orderflow.inventory.domain.InvalidStockAdjustmentException;
import io.github.josepauloferreira.orderflow.ordering.application.IdempotencyConflictException;
import io.github.josepauloferreira.orderflow.ordering.application.InvalidIdempotencyKeyException;
import io.github.josepauloferreira.orderflow.ordering.domain.InvalidOrderException;
import io.github.josepauloferreira.orderflow.ordering.domain.OrderNotFoundException;
import java.util.Comparator;
import java.util.Map;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * One machine-readable HTTP error contract for MVC/domain errors.
 *
 * Do not catch arbitrary RuntimeException or expose server exception messages
 * to callers; unexpected failures keep Spring's default 500 behavior.
 */
@RestControllerAdvice
public class GlobalApiExceptionHandler {

  @ExceptionHandler(ProductNotFoundException.class)
  public ProblemDetail productNotFound(ProductNotFoundException error) {
    return problem(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", error.getMessage());
  }

  @ExceptionHandler(OrderNotFoundException.class)
  public ProblemDetail orderNotFound(OrderNotFoundException error) {
    return problem(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", error.getMessage());
  }

  @ExceptionHandler(DuplicateSkuException.class)
  public ProblemDetail duplicateSku(DuplicateSkuException error) {
    return problem(HttpStatus.CONFLICT, "DUPLICATE_SKU", error.getMessage());
  }

  @ExceptionHandler(InsufficientStockException.class)
  public ProblemDetail insufficientStock(InsufficientStockException error) {
    return problem(HttpStatus.CONFLICT, "INSUFFICIENT_STOCK", error.getMessage());
  }

  @ExceptionHandler(InactiveProductException.class)
  public ProblemDetail inactiveProduct(InactiveProductException error) {
    return problem(HttpStatus.CONFLICT, "PRODUCT_INACTIVE", error.getMessage());
  }

  @ExceptionHandler(IdempotencyConflictException.class)
  public ProblemDetail idempotencyConflict(IdempotencyConflictException error) {
    return problem(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT", error.getMessage());
  }

  @ExceptionHandler(PreconditionRequiredException.class)
  public ProblemDetail preconditionRequired(PreconditionRequiredException error) {
    return problem(HttpStatus.PRECONDITION_REQUIRED, "PRECONDITION_REQUIRED", error.getMessage());
  }

  @ExceptionHandler(ProductVersionMismatchException.class)
  public ProblemDetail versionMismatch(ProductVersionMismatchException error) {
    return problem(HttpStatus.PRECONDITION_FAILED, "STALE_PRODUCT_VERSION", error.getMessage());
  }

  @ExceptionHandler(OptimisticLockingFailureException.class)
  public ProblemDetail optimisticLockFailure(OptimisticLockingFailureException error) {
    return problem(
        HttpStatus.PRECONDITION_FAILED,
        "STALE_PRODUCT_VERSION",
        "Product changed concurrently; read its current ETag before retrying");
  }

  @ExceptionHandler(InvalidProductPreconditionException.class)
  public ProblemDetail invalidPrecondition(InvalidProductPreconditionException error) {
    return problem(HttpStatus.BAD_REQUEST, "INVALID_IF_MATCH", error.getMessage());
  }

  @ExceptionHandler(InvalidIdempotencyKeyException.class)
  public ProblemDetail invalidIdempotencyKey(InvalidIdempotencyKeyException error) {
    return problem(HttpStatus.BAD_REQUEST, "INVALID_IDEMPOTENCY_KEY", error.getMessage());
  }

  @ExceptionHandler(InvalidPaginationException.class)
  public ProblemDetail invalidPagination(InvalidPaginationException error) {
    return problem(HttpStatus.BAD_REQUEST, "INVALID_PAGINATION", error.getMessage());
  }

  @ExceptionHandler({InvalidStockAdjustmentException.class, InvalidOrderException.class})
  public ProblemDetail invalidOperation(RuntimeException error) {
    return problem(HttpStatus.BAD_REQUEST, "INVALID_OPERATION", error.getMessage());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ProblemDetail invalidDto(MethodArgumentNotValidException error) {
    var detail = problem(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed");
    detail.setProperty(
        "violations",
        error.getBindingResult().getFieldErrors().stream()
            .map(field -> Map.of("field", field.getField(), "message",
                field.getDefaultMessage() == null ? "Invalid value" : field.getDefaultMessage()))
            .sorted(Comparator.comparing(item -> item.get("field")))
            .toList());
    return detail;
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ProblemDetail invalidJson(HttpMessageNotReadableException error) {
    return problem(HttpStatus.BAD_REQUEST, "MALFORMED_JSON", "Request body is missing or invalid");
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ProblemDetail invalidParameter(MethodArgumentTypeMismatchException error) {
    return problem(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", "Path or query parameter has an invalid type");
  }

  @ExceptionHandler(MissingRequestHeaderException.class)
  public ProblemDetail missingHeader(MissingRequestHeaderException error) {
    return problem(HttpStatus.BAD_REQUEST, "MISSING_HEADER", "A required request header is missing");
  }

  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  public ProblemDetail unsupportedMediaType(HttpMediaTypeNotSupportedException error) {
    return problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", "Unsupported request Content-Type");
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ProblemDetail unsupportedMethod(HttpRequestMethodNotSupportedException error) {
    return problem(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "Unsupported HTTP method for this endpoint");
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public ProblemDetail noResource(NoResourceFoundException error) {
    return problem(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Resource was not found");
  }

  private static ProblemDetail problem(HttpStatus status, String code, String detail) {
    var body = ProblemDetail.forStatusAndDetail(status, detail);
    body.setProperty("code", code);
    return body;
  }
}
