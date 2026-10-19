package io.github.josepauloferreira.orderflow.ordering.api;

import io.github.josepauloferreira.orderflow.ordering.api.dto.CreateOrderRequest;
import io.github.josepauloferreira.orderflow.ordering.api.dto.OrderResponse;
import io.github.josepauloferreira.orderflow.ordering.application.OrderService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/orders")
public class OrderController {

  private final OrderService orderService;

  @PostMapping
  public ResponseEntity<OrderResponse> create(@Valid @RequestBody CreateOrderRequest request) {
    var order = orderService.create(request);
    return ResponseEntity.created(URI.create("/api/v1/orders/" + order.id())).body(order);
  }

  @GetMapping("/{id}")
  public OrderResponse findById(@PathVariable UUID id) {
    return orderService.findById(id);
  }

  @PostMapping("/{id}/cancel")
  public ResponseEntity<Void> cancel(@PathVariable UUID id) {
    orderService.cancel(id);
    return ResponseEntity.noContent().build();
  }
}
