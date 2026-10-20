package io.github.josepauloferreira.orderflow.ordering.api;

import io.github.josepauloferreira.orderflow.ordering.api.dto.CreateOrderRequest;
import io.github.josepauloferreira.orderflow.ordering.api.dto.OrderResponse;
import io.github.josepauloferreira.orderflow.ordering.application.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Orders", description = "Customer checkout, historical order lookup and cancellation")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/orders")
public class OrderController {

  private final OrderService orderService;

  @Operation(summary = "Confirm an order", description = "CUSTOMER only. Idempotency-Key is scoped by authenticated subject and canonical order content.", security = @SecurityRequirement(name = "bearerAuth"))
  @ApiResponse(responseCode = "201", description = "New order confirmed")
  @ApiResponse(responseCode = "200", description = "Identical request replay; same order ID")
  @ApiResponse(responseCode = "409", description = "Insufficient stock or idempotency conflict")
  @PostMapping
  public ResponseEntity<OrderResponse> create(
      @Valid @RequestBody CreateOrderRequest request,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Parameter(hidden = true) Authentication authentication) {
    var result = orderService.create(request, authentication.getName(), idempotencyKey);
    var uri = URI.create("/api/v1/orders/" + result.order().id());
    if (result.created()) {
      return ResponseEntity.created(uri).body(result.order());
    }
    return ResponseEntity.ok().location(uri).body(result.order());
  }

  @Operation(summary = "Get a historical order", description = "Order owner or ADMIN only. Prices are snapshotted at confirmation.", security = @SecurityRequirement(name = "bearerAuth"))
  @ApiResponse(responseCode = "403", description = "Not the owner or an administrator")
  @GetMapping("/{id}")
  public OrderResponse findById(@PathVariable UUID id, @Parameter(hidden = true) Authentication authentication) {
    return orderService.findById(id, authentication.getName(), hasAdminRole(authentication));
  }

  @Operation(summary = "Cancel a confirmed order", description = "Order owner or ADMIN only. Idempotently restores stock once.", security = @SecurityRequirement(name = "bearerAuth"))
  @ApiResponse(responseCode = "204", description = "Order cancelled or previously cancelled")
  @PostMapping("/{id}/cancel")
  public ResponseEntity<Void> cancel(@PathVariable UUID id, @Parameter(hidden = true) Authentication authentication) {
    orderService.cancel(id, authentication.getName(), hasAdminRole(authentication));
    return ResponseEntity.noContent().build();
  }

  private static boolean hasAdminRole(Authentication authentication) {
    return authentication.getAuthorities().stream()
        .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
  }
}
