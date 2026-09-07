package food_delivery.Platform.deliveryservice.entity;

/**
 * {@code PENDING} — created automatically off {@code OrderPlacedEvent}, no agent yet.
 * {@code ASSIGNED} — an agent has claimed it. {@code PICKED_UP}/{@code DELIVERED} — forward-only
 * progress by the assigned agent. {@code CANCELLED} — the underlying order was cancelled
 * ({@code OrderCancelledEvent}), never a REST-driven transition. {@code DELIVERED}/{@code CANCELLED}
 * are terminal; nothing transitions out of either.
 */
public enum DeliveryStatus {
	PENDING,
	ASSIGNED,
	PICKED_UP,
	DELIVERED,
	CANCELLED
}
