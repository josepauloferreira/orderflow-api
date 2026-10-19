package io.github.josepauloferreira.orderflow.ordering.domain;

import io.github.josepauloferreira.orderflow.catalog.domain.Product;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "order_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderLine {

  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "order_id", nullable = false)
  private CustomerOrder order;

  private UUID productId;
  private String skuSnapshot;
  private String nameSnapshot;
  private BigDecimal unitPrice;
  private int quantity;
  private BigDecimal lineTotal;

  static OrderLine from(CustomerOrder order, Product product, int quantity) {
    var line = new OrderLine();
    line.id = UUID.randomUUID();
    line.order = order;
    line.productId = product.getId();
    line.skuSnapshot = product.getSku();
    line.nameSnapshot = product.getName();
    line.unitPrice = product.getPrice();
    line.quantity = quantity;
    line.lineTotal = product.getPrice().multiply(BigDecimal.valueOf(quantity));
    return line;
  }
}
