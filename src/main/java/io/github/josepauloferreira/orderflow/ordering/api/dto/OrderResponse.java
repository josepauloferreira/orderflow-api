package io.github.josepauloferreira.orderflow.ordering.api.dto;

import io.github.josepauloferreira.orderflow.ordering.domain.CustomerOrder;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
    UUID id, String status, BigDecimal totalAmount, Instant createdAt, List<Item> items) {

  public record Item(
      UUID productId,
      String sku,
      String name,
      int quantity,
      BigDecimal unitPrice,
      BigDecimal lineTotal) {}

  public static OrderResponse from(CustomerOrder order) {
    var items =
        order.getLines().stream()
            .map(
                line ->
                    new Item(
                        line.getProductId(),
                        line.getSkuSnapshot(),
                        line.getNameSnapshot(),
                        line.getQuantity(),
                        line.getUnitPrice(),
                        line.getLineTotal()))
            .toList();
    return new OrderResponse(
        order.getId(),
        order.getStatus().name(),
        order.getTotalAmount(),
        order.getCreatedAt(),
        items);
  }
}
