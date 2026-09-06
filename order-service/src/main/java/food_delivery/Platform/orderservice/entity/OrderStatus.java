package food_delivery.Platform.orderservice.entity;

/**
 * Deliberately minimal — matches exactly the two permission-gated actions FDP's Keycloak roles
 * support today ({@code order:create}, {@code order:cancel}; see docker/keycloak/fdp-realm.json).
 * {@code DELIVERED} isn't added yet: nothing transitions an order to it until
 * {@code delivery-service} exists and publishes/consumes the events that would drive that
 * transition (Sprint 5) — adding an unreachable enum value now would be dead code.
 */
public enum OrderStatus {
	PLACED,
	CANCELLED
}
