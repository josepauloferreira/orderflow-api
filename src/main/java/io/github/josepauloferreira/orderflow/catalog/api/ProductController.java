package io.github.josepauloferreira.orderflow.catalog.api;

import io.github.josepauloferreira.orderflow.catalog.api.dto.CreateProductRequest;
import io.github.josepauloferreira.orderflow.catalog.api.dto.ProductResponse;
import io.github.josepauloferreira.orderflow.catalog.api.dto.UpdateProductRequest;
import io.github.josepauloferreira.orderflow.catalog.application.ProductService;
import io.github.josepauloferreira.orderflow.common.api.PagedResponse;
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

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

  private final ProductService service;

  @PostMapping
  public ResponseEntity<ProductResponse> create(@Valid @RequestBody CreateProductRequest request) {
    ProductResponse response = service.create(request);
    return ResponseEntity.created(URI.create("/api/v1/products/" + response.id())).body(response);
  }

  @GetMapping("/{id}")
  public ProductResponse findById(@PathVariable UUID id) {
    return service.findById(id);
  }

  @GetMapping
  public PagedResponse<ProductResponse> list(
      @RequestParam(required = false) Boolean active,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.list(active, page, size);
  }

  @PutMapping("/{id}")
  public ProductResponse update(
      @PathVariable UUID id, @Valid @RequestBody UpdateProductRequest request) {
    return service.update(id, request);
  }

  @PostMapping("/{id}/deactivate")
  public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
    service.deactivate(id);
    return ResponseEntity.noContent().build();
  }
}
