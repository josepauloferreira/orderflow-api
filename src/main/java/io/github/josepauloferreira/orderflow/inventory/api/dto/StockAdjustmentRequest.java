package io.github.josepauloferreira.orderflow.inventory.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StockAdjustmentRequest(
    @NotNull @Min(-1000000) @Max(1000000) Integer delta,
    @NotBlank @Size(max = 200) String reason) {}
