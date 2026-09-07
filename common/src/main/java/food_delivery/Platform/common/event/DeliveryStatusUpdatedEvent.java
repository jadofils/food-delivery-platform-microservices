package food_delivery.Platform.common.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Published by {@code delivery-service} to the {@code fdp.delivery-events} topic exchange (routing
 * key {@code delivery.status-updated}) whenever a delivery assignment's status changes — claimed by
 * an agent, picked up, or delivered. {@code notification-service} consumes this to record that the
 * customer was notified. See {@link OrderPlacedEvent} for the shared reasoning on why this lives in
 * {@code common} and why it carries {@code customerKeycloakId} rather than an internal id.
 *
 * <p>{@code status} is a plain {@code String}, not {@code delivery-service}'s own
 * {@code DeliveryStatus} enum — a shared event contract must not depend on any one service's
 * internal representation (RULES.md §3); consumers match on the wire values
 * ({@code "ASSIGNED"}/{@code "PICKED_UP"}/{@code "DELIVERED"}) directly.
 */
public record DeliveryStatusUpdatedEvent(
		UUID eventId,
		Long orderId,
		Long deliveryId,
		String customerKeycloakId,
		String status,
		String agentKeycloakId,
		Instant occurredAt) {
}
