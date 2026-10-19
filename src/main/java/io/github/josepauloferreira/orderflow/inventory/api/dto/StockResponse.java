package io.github.josepauloferreira.orderflow.inventory.api.dto;

import java.util.UUID;

public record StockResponse(UUID productId, int availableQuantity) {}
