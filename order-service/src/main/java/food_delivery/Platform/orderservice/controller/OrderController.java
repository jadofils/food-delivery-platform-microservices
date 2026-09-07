package food_delivery.Platform.orderservice.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import food_delivery.Platform.orderservice.dto.OrderResponse;
import food_delivery.Platform.orderservice.dto.OrderSummaryResponse;
import food_delivery.Platform.orderservice.dto.PlaceOrderRequest;
import food_delivery.Platform.orderservice.entity.Order;
import food_delivery.Platform.orderservice.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * Entirely self-service, one permission per action matching Keycloak's own role set exactly
 * ({@code order:create}, {@code order:read}, {@code order:cancel} — RULES.md §8) — there is no
 * admin-wide "view any order" route in this build; see docs/services/order-service.md for why.
 */
@RestController
@RequestMapping("/api/orders/me")
@Tag(name = "Orders")
public class OrderController {

	private final OrderService orderService;

	public OrderController(OrderService orderService) {
		this.orderService = orderService;
	}

	@Operation(summary = "Place an order — validates the restaurant, address, and every menu item via OpenFeign, then publishes OrderPlacedEvent")
	@PreAuthorize("hasAuthority('order:create')")
	@PostMapping
	public ResponseEntity<OrderResponse> placeOrder(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody PlaceOrderRequest request) {
		OrderResponse response = OrderResponse.from(orderService.placeOrder(jwt, request));
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}

	@Operation(summary = "List the caller's own orders (summary view — no item breakdown; see get-by-id for that)")
	@PreAuthorize("hasAuthority('order:read')")
	@GetMapping
	public Page<OrderSummaryResponse> list(@AuthenticationPrincipal Jwt jwt, Pageable pageable) {
		return orderService.listOwn(jwt, pageable).map(OrderSummaryResponse::from);
	}

	@Operation(summary = "Get one of the caller's own orders, including live delivery status (order tracking) when available")
	@PreAuthorize("hasAuthority('order:read')")
	@GetMapping("/{id}")
	public OrderResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
		Order order = orderService.getOwn(jwt, id);
		String deliveryStatus = orderService.getDeliveryStatus(order.getId());
		return OrderResponse.from(order, deliveryStatus);
	}

	@Operation(summary = "Cancel one of the caller's own orders — publishes OrderCancelledEvent")
	@PreAuthorize("hasAuthority('order:cancel')")
	@PostMapping("/{id}/cancel")
	public OrderResponse cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
		return OrderResponse.from(orderService.cancelOwn(jwt, id));
	}

}
