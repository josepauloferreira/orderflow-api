package io.github.josepauloferreira.orderflow.inventory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class StockTest {

  @Test
  void startsEmptyAndAccumulatesAdjustments() {
    var stock = Stock.empty(UUID.randomUUID());
    assertThat(stock.getAvailableQuantity()).isZero();

    stock.change(10);
    stock.change(-3);
    assertThat(stock.getAvailableQuantity()).isEqualTo(7);
  }

  @Test
  void preventsNegativeStockWithoutMutatingState() {
    var id = UUID.randomUUID();
    var stock = Stock.empty(id);
    stock.change(2);

    assertThatThrownBy(() -> stock.change(-3)).isInstanceOf(InsufficientStockException.class);
    assertThat(stock.getAvailableQuantity()).isEqualTo(2);
  }

  @Test
  void rejectsZeroAndOverflow() {
    var stock = Stock.empty(UUID.randomUUID());
    assertThatThrownBy(() -> stock.change(0)).isInstanceOf(InvalidStockAdjustmentException.class);
    stock.change(Integer.MAX_VALUE);
    assertThatThrownBy(() -> stock.change(1)).isInstanceOf(InvalidStockAdjustmentException.class);
    assertThat(stock.getAvailableQuantity()).isEqualTo(Integer.MAX_VALUE);
  }
}
