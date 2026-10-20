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
import org.springframework.web.bind.annotation.RequestHeader;
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
    return ResponseEntity.created(URI.create("/api/v1/products/" + response.id()))
        .eTag(ProductEtag.of(response.version()))
        .body(response);
  }

  @Operation(
      summary = "Get a product by ID",
      description = "Public read; inactive products remain addressable.")
  @ApiResponse(responseCode = "404", description = "Product not found")
  @GetMapping("/{id}")
  public ResponseEntity<ProductResponse> findById(@PathVariable UUID id) {
    ProductResponse response = service.findById(id);
    return ResponseEntity.ok().eTag(ProductEtag.of(response.version())).body(response);
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
      description = "ADMIN only. Requires a strong If-Match ETag from GET; rejects stale writes with 412. SKU cannot be changed.",
      security = @SecurityRequirement(name = "bearerAuth"))
  @ApiResponse(responseCode = "404", description = "Product not found")
  @ApiResponse(responseCode = "428", description = "Missing If-Match precondition")
  @ApiResponse(responseCode = "412", description = "Stale product version")
  @PutMapping("/{id}")
  public ResponseEntity<ProductResponse> update(
      @PathVariable UUID id,
      @Valid @RequestBody UpdateProductRequest request,
      @RequestHeader(value = "If-Match", required = false) String ifMatch) {
    long expected = ProductEtag.require(ifMatch);
    ProductResponse updated = service.update(id, request, expected);
    return ResponseEntity.ok().eTag(ProductEtag.of(updated.version())).body(updated);
  }

  @Operation(
      summary = "Deactivate a product",
      description = "ADMIN only. Requires a strong If-Match ETag; repeated deactivation with the current version has no second effect.",
      security = @SecurityRequirement(name = "bearerAuth"))
  @ApiResponse(responseCode = "204", description = "Product is inactive")
  @ApiResponse(responseCode = "428", description = "Missing If-Match precondition")
  @ApiResponse(responseCode = "412", description = "Stale product version")
  @PostMapping("/{id}/deactivate")
  public ResponseEntity<Void> deactivate(
      @PathVariable UUID id,
      @RequestHeader(value = "If-Match", required = false) String ifMatch) {
    long version = service.deactivate(id, ProductEtag.require(ifMatch));
    return ResponseEntity.noContent().eTag(ProductEtag.of(version)).build();
  }
}
