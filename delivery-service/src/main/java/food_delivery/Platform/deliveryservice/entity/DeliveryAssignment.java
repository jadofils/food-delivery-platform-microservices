package food_delivery.Platform.deliveryservice.entity;

import java.time.Instant;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One delivery assignment per order — created automatically by {@link
 * food_delivery.Platform.deliveryservice.messaging.OrderEventListener} on {@code OrderPlacedEvent},
 * never through a REST call (RULES.md §6). {@link #orderId} carries a unique index (see the Flyway
 * migration): the real idempotency guard against a redelivered event, not just the consumer's own
 * {@code existsByOrderId} pre-check — the same lesson {@code notification-service}'s own
 * find-then-save race already taught this codebase (see its {@code OrderEventListener} javadoc).
 *
 * <p>{@link #customerKeycloakId}/{@link #restaurantId}/{@link #deliveryAddressId} are copied
 * straight off the event, the same "reference id, not a foreign key" pattern {@code order-service}
 * uses for its own cross-service ids (RULES.md §5) — this service never queries {@code order_db},
 * {@code customer_db}, or {@code restaurant_db} directly.
 */
@Entity
@Table(name = "delivery_assignments")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class DeliveryAssignment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "order_id", nullable = false, updatable = false, unique = true)
	private Long orderId;

	@Column(name = "restaurant_id", nullable = false, updatable = false)
	private Long restaurantId;

	@Column(name = "customer_keycloak_id", nullable = false, updatable = false)
	private String customerKeycloakId;

	@Column(name = "delivery_address_id", nullable = false, updatable = false)
	private Long deliveryAddressId;

	@Column(name = "assigned_agent_keycloak_id")
	private String assignedAgentKeycloakId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private DeliveryStatus status = DeliveryStatus.PENDING;

	@Version
	private Long version;

	@CreatedDate
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@LastModifiedDate
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	public DeliveryAssignment(Long orderId, Long restaurantId, String customerKeycloakId, Long deliveryAddressId) {
		this.orderId = orderId;
		this.restaurantId = restaurantId;
		this.customerKeycloakId = customerKeycloakId;
		this.deliveryAddressId = deliveryAddressId;
	}

}
