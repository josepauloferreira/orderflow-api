package io.github.josepauloferreira.orderflow.inventory.application;

import io.github.josepauloferreira.orderflow.catalog.domain.Product;
import io.github.josepauloferreira.orderflow.catalog.domain.ProductNotFoundException;
import io.github.josepauloferreira.orderflow.common.observability.DomainMetrics;
import io.github.josepauloferreira.orderflow.catalog.infrastructure.ProductRepository;
import io.github.josepauloferreira.orderflow.inventory.api.dto.StockAdjustmentRequest;
import io.github.josepauloferreira.orderflow.inventory.api.dto.StockResponse;
import io.github.josepauloferreira.orderflow.inventory.domain.InsufficientStockException;
import io.github.josepauloferreira.orderflow.inventory.domain.InvalidStockAdjustmentException;
import io.github.josepauloferreira.orderflow.inventory.domain.MovementKind;
import io.github.josepauloferreira.orderflow.inventory.domain.Stock;
import io.github.josepauloferreira.orderflow.inventory.domain.StockMovement;
import io.github.josepauloferreira.orderflow.inventory.infrastructure.StockMovementRepository;
import io.github.josepauloferreira.orderflow.inventory.infrastructure.StockRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StockService {

  private final ProductRepository productRepository;
  private final StockRepository stockRepository;
  private final StockMovementRepository movementRepository;
  private final DomainMetrics metrics;

  public StockResponse findByProductId(UUID productId) {
    if (!productRepository.existsById(productId)) {
      throw new ProductNotFoundException(productId);
    }
    return new StockResponse(
        productId, stockRepository.findById(productId).map(Stock::getAvailableQuantity).orElse(0));
  }

  @Transactional
  public StockResponse adjust(UUID productId, StockAdjustmentRequest request) {
    // Lock the parent row even when no inventory row exists yet. This serializes
    // the first adjustment, orders and cancellations for the same product.
    lockProduct(productId);
    if (request.reason() == null || request.reason().isBlank()) {
      throw new InvalidStockAdjustmentException("Stock adjustment reason is required");
    }
    if (request.delta() == null || request.delta() == 0) {
      throw new InvalidStockAdjustmentException("Stock adjustment cannot be zero");
    }
    Stock stock = stockRepository.findById(productId).orElseGet(() -> Stock.empty(productId));
    stock.change(request.delta());
    stockRepository.save(stock);
    movementRepository.save(
        StockMovement.record(
            productId,
            null,
            MovementKind.MANUAL_ADJUSTMENT,
            request.delta(),
            request.reason().strip()));
    metrics.stockAdjusted();
    return new StockResponse(productId, stock.getAvailableQuantity());
  }

  // Called from the order application service in a single outer transaction.
  // Product locks MUST already be held in ascending UUID order.
  @Transactional(propagation = Propagation.MANDATORY)
  public void requireAvailable(UUID productId, int requested) {
    int available = stockRepository.findById(productId).map(Stock::getAvailableQuantity).orElse(0);
    if (available < requested) {
      throw new InsufficientStockException(productId, available, requested);
    }
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void consume(UUID productId, int quantity, UUID orderId) {
    changeForOrder(productId, -quantity, orderId, MovementKind.ORDER_CONFIRMED);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void restore(UUID productId, int quantity, UUID orderId) {
    changeForOrder(productId, quantity, orderId, MovementKind.ORDER_CANCELLED);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public Product lockProduct(UUID productId) {
    return productRepository
        .findByIdForUpdate(productId)
        .orElseThrow(() -> new ProductNotFoundException(productId));
  }

  private void changeForOrder(UUID productId, int delta, UUID orderId, MovementKind kind) {
    Stock stock =
        stockRepository
            .findById(productId)
            .orElseThrow(
                () -> new IllegalStateException("Missing stock row for product " + productId));
    stock.change(delta);
    movementRepository.save(StockMovement.record(productId, orderId, kind, delta, null));
  }
}
