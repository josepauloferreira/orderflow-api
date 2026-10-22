package io.github.josepauloferreira.orderflow.catalog.application;

import io.github.josepauloferreira.orderflow.catalog.api.dto.CreateProductRequest;
import io.github.josepauloferreira.orderflow.catalog.api.dto.ProductResponse;
import io.github.josepauloferreira.orderflow.catalog.api.dto.UpdateProductRequest;
import io.github.josepauloferreira.orderflow.catalog.api.mapper.ProductMapper;
import io.github.josepauloferreira.orderflow.catalog.domain.DuplicateSkuException;
import io.github.josepauloferreira.orderflow.catalog.domain.Product;
import io.github.josepauloferreira.orderflow.catalog.domain.ProductNotFoundException;
import io.github.josepauloferreira.orderflow.catalog.infrastructure.ProductRepository;
import io.github.josepauloferreira.orderflow.common.api.PagedResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

  private static final String UNIQUE_SKU_CONSTRAINT = "uk_products_sku";

  private final ProductRepository repository;
  private final ProductMapper mapper;

  @Transactional
  public ProductResponse create(CreateProductRequest request) {
    Product product =
        Product.create(request.sku(), request.name(), request.description(), request.price());
    try {
      return mapper.toResponse(repository.saveAndFlush(product));
    } catch (DataIntegrityViolationException exception) {
      if (isDuplicateSku(exception)) {
        throw new DuplicateSkuException(product.getSku());
      }
      throw exception;
    }
  }

  public ProductResponse findById(UUID id) {
    return mapper.toResponse(getProduct(id));
  }

  public PagedResponse<ProductResponse> list(Boolean active, int page, int size) {
    if (page < 0 || size < 1 || size > 100) {
      throw new InvalidPaginationException();
    }
    var pageable = PageRequest.of(page, size, Sort.by("createdAt", "id").descending());
    Page<Product> result =
        active == null ? repository.findAll(pageable) : repository.findByActive(active, pageable);
    return PagedResponse.from(result.map(mapper::toResponse));
  }

  @Transactional
  public ProductResponse update(UUID id, UpdateProductRequest request, long expectedVersion) {
    Product product = getProduct(id);
    requireVersion(product, expectedVersion);
    product.updateDetails(request.name(), request.description(), request.price());
    return mapper.toResponse(repository.saveAndFlush(product));
  }

  @Transactional
  public long deactivate(UUID id, long expectedVersion) {
    Product product = getProduct(id);
    requireVersion(product, expectedVersion);
    product.deactivate();
    repository.flush();
    return product.getVersion();
  }

  private static void requireVersion(Product product, long expectedVersion) {
    if (product.getVersion() != expectedVersion) {
      throw new ProductVersionMismatchException();
    }
  }

  private Product getProduct(UUID id) {
    return repository.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
  }

  private static boolean isDuplicateSku(Throwable failure) {
    for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
      if (cause instanceof ConstraintViolationException constraint
          && UNIQUE_SKU_CONSTRAINT.equalsIgnoreCase(constraint.getConstraintName())) {
        return true;
      }
    }
    return false;
  }
}
