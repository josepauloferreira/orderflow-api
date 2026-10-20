package io.github.josepauloferreira.orderflow.inventory.api;

import io.github.josepauloferreira.orderflow.inventory.api.dto.StockAdjustmentRequest;
import io.github.josepauloferreira.orderflow.inventory.api.dto.StockResponse;
import io.github.josepauloferreira.orderflow.inventory.application.StockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Inventory", description = "Administrative stock queries and signed adjustments")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/products/{productId}/stock")
public class StockController {

  private final StockService stockService;

  @Operation(
      summary = "Get available stock",
      description = "ADMIN only. Returns zero before the first adjustment.",
      security = @SecurityRequirement(name = "bearerAuth"))
  @GetMapping
  public StockResponse get(@PathVariable UUID productId) {
    return stockService.findByProductId(productId);
  }

  @Operation(
      summary = "Adjust available stock",
      description = "ADMIN only. Nonzero signed delta and reason; negative stock is rejected.",
      security = @SecurityRequirement(name = "bearerAuth"))
  @ApiResponse(responseCode = "409", description = "Insufficient stock")
  @PostMapping("/adjustments")
  public StockResponse adjust(
      @PathVariable UUID productId, @Valid @RequestBody StockAdjustmentRequest request) {
    return stockService.adjust(productId, request);
  }
}
