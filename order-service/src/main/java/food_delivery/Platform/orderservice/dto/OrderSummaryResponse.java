package food_delivery.Platform.orderservice.dto;

import java.math.BigDecimal;
import java.time.Instant;

import food_delivery.Platform.orderservice.entity.Order;
import food_delivery.Platform.orderservice.entity.OrderStatus;

/**
 * The list view deliberately omits {@code items} — {@code OrderRepository#findByCustomerKeycloakId}
 * doesn't fetch the LAZY association (RULES.md — "no unnecessary eager loading"; a listing doesn't
 * need every order's full item breakdown). Serializing it here would throw
 * {@code LazyInitializationException}: the Hibernate session is already closed by the time Jackson
 * writes the response, since {@code OrderService#listOwn}'s {@code @Transactional} already
 * committed by then — caught by this service's own integration test. {@link OrderResponse} (with
 * items) is reserved for the single get-by-id route, which uses the {@code @EntityGraph} query
 * that actually loads them.
 */
public record OrderSummaryResponse(
		Long id,
		Long restaurantId,
		Long deliveryAddressId,
		OrderStatus status,
		BigDecimal totalAmount,
		Instant createdAt) {

	public static OrderSummaryResponse from(Order order) {
		return new OrderSummaryResponse(
				order.getId(),
				order.getRestaurantId(),
				order.getDeliveryAddressId(),
				order.getStatus(),
				order.getTotalAmount(),
				order.getCreatedAt());
	}

}
