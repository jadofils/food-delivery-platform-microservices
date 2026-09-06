/**
 * Domain event payloads published to RabbitMQ (RULES.md §6) — data only, no behavior. These live
 * in {@code common} because the publisher and every consumer must agree on the exact wire shape
 * atomically (RULES.md §3): a producer-only change to a field name here silently breaks every
 * consumer's deserialization, which is exactly the kind of drift {@code common} exists to prevent.
 *
 * <p>Naming convention: {@code <Entity><PastTenseVerb>Event} (RULES.md §6) —
 * {@link food_delivery.Platform.common.event.OrderPlacedEvent},
 * {@link food_delivery.Platform.common.event.OrderCancelledEvent}. Every event carries an
 * {@code eventId} so a consumer can dedupe on it — RabbitMQ guarantees at-least-once delivery, not
 * exactly-once, so every consumer must be idempotent (RULES.md §6, §1 factor 9).
 */
package food_delivery.Platform.common.event;
