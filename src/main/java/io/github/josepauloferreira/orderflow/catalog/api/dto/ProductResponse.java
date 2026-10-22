package io.github.josepauloferreira.orderflow.catalog.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ProductResponse(
    UUID id,
    long version,
    String sku,
    String name,
    String description,
    BigDecimal price,
    boolean active,
    Instant createdAt,
    Instant updatedAt) {}
