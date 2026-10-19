package io.github.josepauloferreira.orderflow.ordering.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record CreateOrderRequest(@NotEmpty @Size(max = 20) List<@NotNull @Valid Item> items) {

  public record Item(@NotNull UUID productId, @NotNull @Min(1) @Max(1000) Integer quantity) {}
}
