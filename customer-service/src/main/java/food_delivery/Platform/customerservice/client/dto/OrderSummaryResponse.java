package food_delivery.Platform.customerservice.client.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * customer-service's own minimal view of {@code order-service}'s order summary — mirrors the
 * shape of {@code order-service}'s own {@code OrderSummaryResponse} field-for-field (including
 * {@code deliveryStatus}, which that service's own list route already enriches per row via its
 * own Feign call to {@code delivery-service}), but this record is owned here, not imported from
 * order-service (RULES.md §3: the Feign client — and its response DTOs — belong to the caller, so
 * order-service can change its own response shape without this service picking up a shared-module
 * change). {@code status}/{@code deliveryStatus} are plain {@code String}, not order-service's own
 * {@code OrderStatus} enum or an equivalent duplicated one — the same "don't duplicate someone
 * else's enum, just read the string" choice {@code DeliveryStatusResponse} already made for
 * delivery-service's status.
 */
public record OrderSummaryResponse(
		Long id,
		Long restaurantId,
		String status,
		BigDecimal totalAmount,
		Instant createdAt,
		String deliveryStatus) {
}
