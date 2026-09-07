package food_delivery.Platform.orderservice.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import food_delivery.Platform.orderservice.entity.Order;
import food_delivery.Platform.orderservice.entity.OrderStatus;

public record OrderResponse(
		Long id,
		Long restaurantId,
		Long deliveryAddressId,
		OrderStatus status,
		BigDecimal totalAmount,
		List<OrderItemResponse> items,
		Instant createdAt,
		String deliveryStatus) {

	/**
	 * Used by placement/cancellation, where a live delivery-service round trip adds nothing (a
	 * just-placed order has no assignment yet; a just-cancelled one's assignment cancellation is
	 * itself async) — {@code deliveryStatus} is {@code null} here, not fetched.
	 */
	public static OrderResponse from(Order order) {
		return from(order, null);
	}

	/** Used by the get-by-id route, which enriches with delivery-service's live status (RULES.md §6). */
	public static OrderResponse from(Order order, String deliveryStatus) {
		return new OrderResponse(
				order.getId(),
				order.getRestaurantId(),
				order.getDeliveryAddressId(),
				order.getStatus(),
				order.getTotalAmount(),
				order.getItems().stream().map(OrderItemResponse::from).toList(),
				order.getCreatedAt(),
				deliveryStatus);
	}

}
