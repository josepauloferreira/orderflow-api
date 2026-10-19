package io.github.josepauloferreira.orderflow.catalog.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "products")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product {

  private static final Pattern VALID_SKU = Pattern.compile("^[A-Z0-9][A-Z0-9_-]{2,39}$");
  private static final BigDecimal MAX_PRICE = new BigDecimal("9999999999.99");

  @Id private UUID id;

  private String sku;
  private String name;
  private String description;
  private BigDecimal price;
  private boolean active;
  private Instant createdAt;
  private Instant updatedAt;

  public static Product create(String sku, String name, String description, BigDecimal price) {
    var product = new Product();
    product.id = UUID.randomUUID();
    product.sku = normalizeSku(sku);
    product.replaceDetails(name, description, price);
    product.active = true;
    product.createdAt = Instant.now();
    product.updatedAt = product.createdAt;
    return product;
  }

  public void updateDetails(String name, String description, BigDecimal price) {
    replaceDetails(name, description, price);
    updatedAt = Instant.now();
  }

  public void deactivate() {
    if (active) {
      active = false;
      updatedAt = Instant.now();
    }
  }

  private void replaceDetails(String name, String description, BigDecimal price) {
    if (name == null || name.isBlank() || name.strip().length() > 120) {
      throw new IllegalArgumentException("Product name must have 1 to 120 characters");
    }
    var normalizedDescription = description == null ? null : description.strip();
    if (normalizedDescription != null && normalizedDescription.length() > 1000) {
      throw new IllegalArgumentException("Product description must have at most 1000 characters");
    }
    if (price == null
        || price.signum() <= 0
        || price.compareTo(MAX_PRICE) > 0
        || price.scale() > 2) {
      throw new IllegalArgumentException(
          "Product price must be positive and have at most 2 decimals");
    }
    this.name = name.strip();
    this.description =
        normalizedDescription == null || normalizedDescription.isEmpty()
            ? null
            : normalizedDescription;
    this.price = price.setScale(2, RoundingMode.UNNECESSARY);
  }

  private static String normalizeSku(String value) {
    if (value == null) {
      throw new IllegalArgumentException("Product SKU is required");
    }
    String normalized = value.strip().toUpperCase(Locale.ROOT);
    if (!VALID_SKU.matcher(normalized).matches()) {
      throw new IllegalArgumentException("Product SKU format is invalid");
    }
    return normalized;
  }
}
