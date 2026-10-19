package io.github.josepauloferreira.orderflow.catalog.api.mapper;

import io.github.josepauloferreira.orderflow.catalog.api.dto.ProductResponse;
import io.github.josepauloferreira.orderflow.catalog.domain.Product;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ProductMapper {

  ProductResponse toResponse(Product product);
}
