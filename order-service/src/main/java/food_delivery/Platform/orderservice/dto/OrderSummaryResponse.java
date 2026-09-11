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
		Instant createdAt,
		String deliveryStatus) {

	/**
	 * Used where a live delivery-service round trip isn't warranted for every row (none today, but
	 * kept for symmetry with {@code OrderResponse#from(Order)}) — {@code deliveryStatus} is
	 * {@code null} here, not fetched.
	 */
	public static OrderSummaryResponse from(Order order) {
		return from(order, null);
	}

	/**
	 * Used by the list route, which enriches every row with delivery-service's live status
	 * (RULES.md §6) — one Feign call per row on the current page, not batched. Acceptable for a
	 * bounded page size (Pageable's own default/max), and consistent with the same
	 * one-call-per-order pattern the single get-by-id route already uses; see
	 * {@code DeliveryServiceGateway}'s own class comment for why a failed/slow call degrades to
	 * {@code null} instead of failing the whole list.
	 */
	public static OrderSummaryResponse from(Order order, String deliveryStatus) {
		return new OrderSummaryResponse(
				order.getId(),
				order.getRestaurantId(),
				order.getDeliveryAddressId(),
				order.getStatus(),
				order.getTotalAmount(),
				order.getCreatedAt(),
				deliveryStatus);
	}

}
