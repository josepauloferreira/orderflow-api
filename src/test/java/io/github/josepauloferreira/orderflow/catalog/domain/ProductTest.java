package io.github.josepauloferreira.orderflow.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ProductTest {

  @Test
  void normalizesSkuNameDescriptionAndPrice() {
    var product =
        Product.create(
            " ab-123 ", "  Mechanical Keyboard ", "  Quiet switches  ", new BigDecimal("129.9"));

    assertThat(product.getId()).isNotNull();
    assertThat(product.getSku()).isEqualTo("AB-123");
    assertThat(product.getName()).isEqualTo("Mechanical Keyboard");
    assertThat(product.getDescription()).isEqualTo("Quiet switches");
    assertThat(product.getPrice()).isEqualByComparingTo("129.90");
    assertThat(product.isActive()).isTrue();
    assertThat(product.getCreatedAt()).isEqualTo(product.getUpdatedAt());
  }

  @Test
  void rejectsPriceBeyondColumnPrecisionOrScale() {
    assertThatThrownBy(() -> Product.create("SKU-1", "Product", null, new BigDecimal("-1.00")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Product.create("SKU-1", "Product", null, new BigDecimal("1.001")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> Product.create("SKU-1", "Product", null, new BigDecimal("10000000000.00")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsInvalidSkuAndBlankName() {
    assertThatThrownBy(() -> Product.create(" a ", "Product", null, BigDecimal.ONE))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Product.create("ABC-1", "  ", null, BigDecimal.ONE))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void updatesEditableFieldsWithoutChangingIdentityOrSku() {
    var product = Product.create("SKU-1", "Original", null, BigDecimal.ONE);
    var id = product.getId();

    product.updateDetails(" Updated ", " ", new BigDecimal("11.00"));

    assertThat(product.getId()).isEqualTo(id);
    assertThat(product.getSku()).isEqualTo("SKU-1");
    assertThat(product.getName()).isEqualTo("Updated");
    assertThat(product.getDescription()).isNull();
    assertThat(product.getPrice()).isEqualByComparingTo("11.00");
  }

  @Test
  void deactivationIsIdempotent() {
    var product = Product.create("SKU-1", "Original", null, BigDecimal.ONE);
    product.deactivate();
    var updatedAt = product.getUpdatedAt();

    product.deactivate();

    assertThat(product.isActive()).isFalse();
    assertThat(product.getUpdatedAt()).isEqualTo(updatedAt);
  }
}
