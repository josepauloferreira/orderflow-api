package io.github.josepauloferreira.orderflow.common.api;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
    info =
        @Info(
            title = "OrderFlow API",
            version = "v1",
            description =
                "Transactional product catalog, inventory and order management. "
                    + "Order creation requires an Idempotency-Key and a CUSTOMER JWT; "
                    + "catalog and stock mutations require ADMIN."))
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT")
public class OpenApiConfig {}
