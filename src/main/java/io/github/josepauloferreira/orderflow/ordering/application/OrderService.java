package io.github.josepauloferreira.orderflow.ordering.application;

import io.github.josepauloferreira.orderflow.catalog.domain.Product;
import io.github.josepauloferreira.orderflow.inventory.application.StockService;
import io.github.josepauloferreira.orderflow.inventory.domain.InactiveProductException;
import io.github.josepauloferreira.orderflow.ordering.api.dto.CreateOrderRequest;
import io.github.josepauloferreira.orderflow.ordering.api.dto.OrderResponse;
import io.github.josepauloferreira.orderflow.ordering.domain.CustomerOrder;
import io.github.josepauloferreira.orderflow.ordering.domain.InvalidOrderException;
import io.github.josepauloferreira.orderflow.ordering.domain.OrderNotFoundException;
import io.github.josepauloferreira.orderflow.ordering.domain.OrderStatus;
import io.github.josepauloferreira.orderflow.ordering.infrastructure.OrderRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

  private final StockService stockService;
  private final OrderRepository orderRepository;

  @Transactional
  public OrderResponse create(CreateOrderRequest request) {
    // Reject duplicate IDs instead of silently merging or overwriting quantities.
    var requested = new LinkedHashMap<UUID, Integer>();
    for (var item : request.items()) {
      if (requested.putIfAbsent(item.productId(), item.quantity()) != null) {
        throw new InvalidOrderException("Each product must occur only once in an order");
      }
    }
    if (requested.isEmpty()) {
      throw new InvalidOrderException("An order needs at least one item");
    }

    // Acquiring locks in the same deterministic order prevents cross-order deadlocks
    // for overlapping product sets, including manual adjustments and cancellations.
    var ids = new ArrayList<>(requested.keySet());
    ids.sort(UUID::compareTo);
    Map<Product, Integer> selected = new LinkedHashMap<>();
    for (UUID id : ids) {
      Product product = stockService.lockProduct(id);
      if (!product.isActive()) {
        throw new InactiveProductException(id);
      }
      stockService.requireAvailable(id, requested.get(id));
      selected.put(product, requested.get(id));
    }

    CustomerOrder order = CustomerOrder.confirm(selected);
    // Flush the order and its lines before inserting movements with a FK to it.
    orderRepository.saveAndFlush(order);
    for (var entry : selected.entrySet()) {
      stockService.consume(entry.getKey().getId(), entry.getValue(), order.getId());
    }
    return OrderResponse.from(order);
  }

  public OrderResponse findById(UUID id) {
    return OrderResponse.from(
        orderRepository.findById(id).orElseThrow(() -> new OrderNotFoundException(id)));
  }

  @Transactional
  public void cancel(UUID id) {
    // Serialize repeated cancellations first, then lock products in UUID order.
    var order =
        orderRepository.findByIdForUpdate(id).orElseThrow(() -> new OrderNotFoundException(id));
    if (order.getStatus() == OrderStatus.CANCELLED) {
      return;
    }

    var lines = new ArrayList<>(order.getLines());
    lines.sort(Comparator.comparing(line -> line.getProductId()));
    for (var line : lines) {
      stockService.lockProduct(line.getProductId());
    }
    for (var line : lines) {
      stockService.restore(line.getProductId(), line.getQuantity(), id);
    }
    order.cancel();
  }
}
