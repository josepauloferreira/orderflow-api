package io.github.josepauloferreira.orderflow.catalog.api;

import io.github.josepauloferreira.orderflow.catalog.api.dto.CreateProductRequest;
import io.github.josepauloferreira.orderflow.catalog.api.dto.ProductResponse;
import io.github.josepauloferreira.orderflow.catalog.api.dto.UpdateProductRequest;
import io.github.josepauloferreira.orderflow.catalog.application.ProductService;
import io.github.josepauloferreira.orderflow.common.api.PagedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Products", description = "Public catalog queries and administrative product mutations")
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

  private final ProductService service;

  @Operation(
      summary = "Create a product",
      description = "ADMIN only. SKU is normalized and immutable after creation.",
      security = @SecurityRequirement(name = "bearerAuth"))
  @ApiResponse(
      responseCode = "201",
      description = "Product created; Location points to the resource")
  @ApiResponse(responseCode = "409", description = "Duplicate SKU")
  @PostMapping
  public ResponseEntity<ProductResponse> create(@Valid @RequestBody CreateProductRequest request) {
    ProductResponse response = service.create(request);
    return ResponseEntity.created(URI.create("/api/v1/products/" + response.id())).body(response);
  }

  @Operation(
      summary = "Get a product by ID",
      description = "Public read; inactive products remain addressable.")
  @ApiResponse(responseCode = "404", description = "Product not found")
  @GetMapping("/{id}")
  public ProductResponse findById(@PathVariable UUID id) {
    return service.findById(id);
  }

  @Operation(
      summary = "List products",
      description = "Public paginated catalog. Page starts at zero; size must be 1–100.")
  @GetMapping
  public PagedResponse<ProductResponse> list(
      @RequestParam(required = false) Boolean active,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.list(active, page, size);
  }

  @Operation(
      summary = "Replace editable product details",
      description = "ADMIN only. SKU cannot be changed.",
      security = @SecurityRequirement(name = "bearerAuth"))
  @ApiResponse(responseCode = "404", description = "Product not found")
  @PutMapping("/{id}")
  public ProductResponse update(
      @PathVariable UUID id, @Valid @RequestBody UpdateProductRequest request) {
    return service.update(id, request);
  }

  @Operation(
      summary = "Deactivate a product",
      description = "ADMIN only. Idempotent; does not delete historical orders.",
      security = @SecurityRequirement(name = "bearerAuth"))
  @ApiResponse(responseCode = "204", description = "Product is inactive")
  @PostMapping("/{id}/deactivate")
  public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
    service.deactivate(id);
    return ResponseEntity.noContent().build();
  }
}
