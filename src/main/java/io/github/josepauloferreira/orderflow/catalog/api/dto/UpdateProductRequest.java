package io.github.josepauloferreira.orderflow.catalog.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record UpdateProductRequest(
    @NotBlank @Size(max = 120) String name,
    @Size(max = 1000) String description,
    @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal price) {}
