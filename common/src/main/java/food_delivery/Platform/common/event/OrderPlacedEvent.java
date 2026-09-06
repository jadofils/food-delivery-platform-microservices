package food_delivery.Platform.common.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Published by {@code order-service} to the {@code fdp.order-events} topic exchange (routing key
 * {@code order.placed}) once an order is validated and saved. {@code delivery-service} will
 * consume this to auto-create a delivery assignment, and {@code notification-service} to record
 * that the customer was notified (both Sprint 5, not built yet) — see RULES.md §6.
 *
 * <p>{@code customerKeycloakId}, not a {@code customer-service}-internal id: consumers that need
 * to reach the customer (e.g. to notify them) only have the identity every service already
 * validates tokens against, not a foreign key into a database they don't own (RULES.md §5).
 */
public record OrderPlacedEvent(
		UUID eventId,
		Long orderId,
		String customerKeycloakId,
		Long restaurantId,
		Long deliveryAddressId,
		BigDecimal totalAmount,
		List<Item> items,
		Instant occurredAt) {

	public record Item(Long menuItemId, String name, BigDecimal unitPrice, int quantity) {
	}

}
