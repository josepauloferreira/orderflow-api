package io.github.josepauloferreira.orderflow.inventory.api;

import io.github.josepauloferreira.orderflow.inventory.api.dto.StockAdjustmentRequest;
import io.github.josepauloferreira.orderflow.inventory.api.dto.StockResponse;
import io.github.josepauloferreira.orderflow.inventory.application.StockService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/products/{productId}/stock")
public class StockController {

  private final StockService stockService;

  @GetMapping
  public StockResponse get(@PathVariable UUID productId) {
    return stockService.findByProductId(productId);
  }

  @PostMapping("/adjustments")
  public StockResponse adjust(
      @PathVariable UUID productId, @Valid @RequestBody StockAdjustmentRequest request) {
    return stockService.adjust(productId, request);
  }
}
