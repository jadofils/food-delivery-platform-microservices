package food_delivery.Platform.orderservice.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * {@code restaurantId}/{@code deliveryAddressId} are validated against real
 * {@code restaurant-service}/{@code customer-service} data via OpenFeign before an {@code Order}
 * is ever created (RULES.md §6) — this DTO only enforces the request is well-formed, not that
 * those ids actually exist.
 */
public record PlaceOrderRequest(

		@NotNull(message = "restaurantId is required") Long restaurantId,

		@NotNull(message = "deliveryAddressId is required") Long deliveryAddressId,

		@NotEmpty(message = "items must not be empty") @Valid List<OrderItemRequest> items) {
}
