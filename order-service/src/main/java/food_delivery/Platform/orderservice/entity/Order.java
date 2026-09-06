package food_delivery.Platform.orderservice.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * An order placed by a customer against one restaurant. Keyed to the placing customer by
 * {@link #customerKeycloakId} ({@code sub} claim, same pattern as {@code Customer}/
 * {@code Restaurant} — RULES.md §8) rather than {@code customer-service}'s internal id, since
 * that's the identity {@code order-service} actually validates tokens against. {@link #customerId}
 * is still recorded, purely as a cross-service reference captured from the Feign lookup at
 * placement time (RULES.md §5 — no foreign key, no join, just an id order-service owns a copy of).
 *
 * <p>{@link #restaurantId}/{@link #deliveryAddressId} are the same kind of reference id — order-service
 * never queries {@code restaurant_db}/{@code customer_db} directly, it validated both via
 * OpenFeign before this entity was ever created.
 *
 * <p>{@code items} is {@link FetchType#LAZY} on purpose (RULES.md — "no unnecessary eager
 * loading") — see {@code OrderRepository#findWithItemsById} for the one query that needs them.
 */
@Entity
@Table(name = "orders")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class Order {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "customer_keycloak_id", nullable = false, updatable = false)
	private String customerKeycloakId;

	@Column(name = "customer_id", nullable = false, updatable = false)
	private Long customerId;

	@Column(name = "restaurant_id", nullable = false, updatable = false)
	private Long restaurantId;

	@Column(name = "delivery_address_id", nullable = false, updatable = false)
	private Long deliveryAddressId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private OrderStatus status = OrderStatus.PLACED;

	@Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
	private BigDecimal totalAmount;

	@OneToMany(mappedBy = "order", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
	private List<OrderItem> items = new ArrayList<>();

	@Version
	private Long version;

	@CreatedDate
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@LastModifiedDate
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	public Order(String customerKeycloakId, Long customerId, Long restaurantId, Long deliveryAddressId,
			BigDecimal totalAmount) {
		this.customerKeycloakId = customerKeycloakId;
		this.customerId = customerId;
		this.restaurantId = restaurantId;
		this.deliveryAddressId = deliveryAddressId;
		this.totalAmount = totalAmount;
	}

	public void addItem(OrderItem item) {
		items.add(item);
		item.setOrder(this);
	}

}
