package food_delivery.Platform.common.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Published by {@code order-service} to the {@code fdp.order-events} topic exchange (routing key
 * {@code order.cancelled}) once a placed order is cancelled. See {@link OrderPlacedEvent} for the
 * shared reasoning on why this lives in {@code common} and why it carries
 * {@code customerKeycloakId} rather than an internal customer id.
 */
public record OrderCancelledEvent(
		UUID eventId,
		Long orderId,
		String customerKeycloakId,
		Long restaurantId,
		Instant occurredAt) {
}
