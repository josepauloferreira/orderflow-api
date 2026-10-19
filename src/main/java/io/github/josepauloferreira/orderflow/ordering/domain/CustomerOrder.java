package io.github.josepauloferreira.orderflow.ordering.domain;

import io.github.josepauloferreira.orderflow.catalog.domain.Product;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "customer_orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CustomerOrder {

  private static final BigDecimal MAX_TOTAL = new BigDecimal("999999999999.99");

  @Id private UUID id;

  @Enumerated(EnumType.STRING)
  private OrderStatus status;

  private String customerSubject;
  private BigDecimal totalAmount;
  private Instant createdAt;
  private Instant updatedAt;

  @Getter(AccessLevel.NONE)
  @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<OrderLine> lines = new ArrayList<>();

  public static CustomerOrder confirm(Map<Product, Integer> items, String customerSubject) {
    if (items.isEmpty()) {
      throw new InvalidOrderException("An order needs at least one item");
    }
    var order = new CustomerOrder();
    if (customerSubject == null || customerSubject.isBlank() || customerSubject.length() > 200) {
      throw new InvalidOrderException("Valid customer identity required");
    }
    order.id = UUID.randomUUID();
    order.customerSubject = customerSubject;
    order.status = OrderStatus.CONFIRMED;
    order.createdAt = Instant.now();
    order.updatedAt = order.createdAt;
    for (var item : items.entrySet()) {
      if (item.getValue() == null || item.getValue() < 1) {
        throw new InvalidOrderException("Order quantities must be positive");
      }
      order.lines.add(OrderLine.from(order, item.getKey(), item.getValue()));
    }
    order.totalAmount =
        order.lines.stream().map(OrderLine::getLineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    if (order.totalAmount.compareTo(MAX_TOTAL) > 0) {
      throw new InvalidOrderException("Order total exceeds supported precision");
    }
    return order;
  }

  public List<OrderLine> getLines() {
    return List.copyOf(lines);
  }

  public boolean cancel() {
    if (status == OrderStatus.CANCELLED) {
      return false;
    }
    status = OrderStatus.CANCELLED;
    updatedAt = Instant.now();
    return true;
  }
}
