package io.github.josepauloferreira.orderflow.ordering.api;

import io.github.josepauloferreira.orderflow.ordering.api.dto.CreateOrderRequest;
import io.github.josepauloferreira.orderflow.ordering.api.dto.OrderResponse;
import io.github.josepauloferreira.orderflow.ordering.application.OrderService;
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

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/orders")
public class OrderController {

  private final OrderService orderService;

  @PostMapping
  public ResponseEntity<OrderResponse> create(
      @Valid @RequestBody CreateOrderRequest request,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      Authentication authentication) {
    var result = orderService.create(request, authentication.getName(), idempotencyKey);
    var uri = URI.create("/api/v1/orders/" + result.order().id());
    if (result.created()) {
      return ResponseEntity.created(uri).body(result.order());
    }
    return ResponseEntity.ok().location(uri).body(result.order());
  }

  @GetMapping("/{id}")
  public OrderResponse findById(@PathVariable UUID id, Authentication authentication) {
    return orderService.findById(
        id, authentication.getName(), hasAdminRole(authentication));
  }

  @PostMapping("/{id}/cancel")
  public ResponseEntity<Void> cancel(@PathVariable UUID id, Authentication authentication) {
    orderService.cancel(id, authentication.getName(), hasAdminRole(authentication));
    return ResponseEntity.noContent().build();
  }

  private static boolean hasAdminRole(Authentication authentication) {
    return authentication.getAuthorities().stream()
        .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
  }
}
