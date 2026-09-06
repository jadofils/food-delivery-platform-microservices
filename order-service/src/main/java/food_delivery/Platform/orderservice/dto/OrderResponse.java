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
		Instant createdAt) {

	public static OrderResponse from(Order order) {
		return new OrderResponse(
				order.getId(),
				order.getRestaurantId(),
				order.getDeliveryAddressId(),
				order.getStatus(),
				order.getTotalAmount(),
				order.getItems().stream().map(OrderItemResponse::from).toList(),
				order.getCreatedAt());
	}

}
