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
import io.github.josepauloferreira.orderflow.ordering.infrastructure.OrderIdempotencyRepository;
import io.github.josepauloferreira.orderflow.ordering.infrastructure.OrderRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

  private static final Pattern VALID_KEY = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._:-]{7,127}$");

  private final StockService stockService;
  private final OrderRepository orderRepository;
  private final OrderIdempotencyRepository idempotency;

  public record CreateResult(OrderResponse order, boolean created) {}

  @Transactional
  public CreateResult create(
      CreateOrderRequest request, String customerSubject, String idempotencyKey) {
    if (customerSubject == null || customerSubject.isBlank() || customerSubject.length() > 200) {
      throw new InvalidOrderException("A valid authenticated customer is required");
    }
    if (idempotencyKey == null || !VALID_KEY.matcher(idempotencyKey).matches()) {
      throw new InvalidIdempotencyKeyException();
    }
    var requested = new LinkedHashMap<UUID, Integer>();
    if (request == null
        || request.items() == null
        || request.items().isEmpty()
        || request.items().size() > 20) {
      throw new InvalidOrderException("An order requires 1 to 20 items");
    }
    for (var item : request.items()) {
      if (item == null
          || item.productId() == null
          || item.quantity() == null
          || item.quantity() < 1
          || item.quantity() > 1000) {
        throw new InvalidOrderException("Invalid order item");
      }
      if (requested.putIfAbsent(item.productId(), item.quantity()) != null) {
        throw new InvalidOrderException("Each product must occur only once in an order");
      }
    }

    // Canonical ordering means payload permutations are semantically equivalent.
    var ids = new ArrayList<>(requested.keySet());
    ids.sort(UUID::compareTo);
    String digest = digest(ids, requested);
    UUID alreadyCreated = idempotency.claim(customerSubject, idempotencyKey, digest);
    if (alreadyCreated != null) {
      return new CreateResult(findById(alreadyCreated, customerSubject, false), false);
    }

    // All product locks are acquired in ascending UUID order.
    Map<Product, Integer> selected = new LinkedHashMap<>();
    for (UUID id : ids) {
      Product product = stockService.lockProduct(id);
      if (!product.isActive()) {
        throw new InactiveProductException(id);
      }
      stockService.requireAvailable(id, requested.get(id));
      selected.put(product, requested.get(id));
    }

    CustomerOrder order = CustomerOrder.confirm(selected, customerSubject);
    orderRepository.saveAndFlush(order);
    for (var entry : selected.entrySet()) {
      stockService.consume(entry.getKey().getId(), entry.getValue(), order.getId());
    }
    idempotency.complete(customerSubject, idempotencyKey, order.getId());
    return new CreateResult(OrderResponse.from(order), true);
  }

  public OrderResponse findById(UUID id, String customerSubject, boolean administrator) {
    var order = orderRepository.findById(id).orElseThrow(() -> new OrderNotFoundException(id));
    authorize(order, customerSubject, administrator);
    return OrderResponse.from(order);
  }

  @Transactional
  public void cancel(UUID id, String customerSubject, boolean administrator) {
    var order =
        orderRepository.findByIdForUpdate(id).orElseThrow(() -> new OrderNotFoundException(id));
    authorize(order, customerSubject, administrator);
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

  private static void authorize(
      CustomerOrder order, String customerSubject, boolean administrator) {
    if (!administrator && !order.getCustomerSubject().equals(customerSubject)) {
      throw new AccessDeniedException("You cannot access another customer's order");
    }
  }

  private static String digest(java.util.List<UUID> ids, Map<UUID, Integer> requested) {
    var canonical = new StringBuilder("order-v1\n");
    for (UUID id : ids) {
      canonical.append(id).append(':').append(requested.get(id)).append('\n');
    }
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256")
              .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
      return java.util.HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 unavailable", impossible);
    }
  }
}
