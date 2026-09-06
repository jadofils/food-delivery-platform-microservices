package food_delivery.Platform.restaurantservice.entity;

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
 * A restaurant's FDP profile, keyed to Keycloak by {@link #ownerKeycloakId} ({@code sub} claim,
 * RULES.md §8) — same pattern as {@code customer-service}'s {@code Customer}: this service never
 * stores a credential, Keycloak owns the owner's identity entirely.
 *
 * <p>One restaurant per owner for now — a deliberate MVP simplification (a real platform would
 * let one owner manage several restaurants), kept consistent with {@code customer-service}'s
 * one-profile-per-identity pattern rather than adding multi-restaurant management nothing in
 * {@code ReadMe.md}'s scope actually asks for yet.
 *
 * <p>{@code menuItems} is {@link FetchType#LAZY} on purpose (RULES.md — "no unnecessary eager
 * loading") — see {@code RestaurantRepository#findWithMenuItemsById} for the one query that uses
 * an {@code @EntityGraph} to load them together instead.
 */
@Entity
@Table(name = "restaurants")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class Restaurant {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "owner_keycloak_id", nullable = false, unique = true, updatable = false)
	private String ownerKeycloakId;

	@Column(nullable = false)
	private String name;

	@Column
	private String description;

	@Column(name = "cuisine_type", nullable = false)
	private String cuisineType;

	@Column(nullable = false)
	private String street;

	@Column(nullable = false)
	private String city;

	@Column(nullable = false)
	private String state;

	@Column(name = "postal_code", nullable = false)
	private String postalCode;

	@Column(nullable = false)
	private String country;

	@Column(name = "is_open", nullable = false)
	private boolean isOpen = true;

	@OneToMany(mappedBy = "restaurant", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
	private List<MenuItem> menuItems = new ArrayList<>();

	@Version
	private Long version;

	@CreatedDate
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@LastModifiedDate
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	public Restaurant(String ownerKeycloakId, String name, String description, String cuisineType, String street,
			String city, String state, String postalCode, String country) {
		this.ownerKeycloakId = ownerKeycloakId;
		this.name = name;
		this.description = description;
		this.cuisineType = cuisineType;
		this.street = street;
		this.city = city;
		this.state = state;
		this.postalCode = postalCode;
		this.country = country;
	}

	public void addMenuItem(MenuItem menuItem) {
		menuItems.add(menuItem);
		menuItem.setRestaurant(this);
	}

	public void removeMenuItem(MenuItem menuItem) {
		menuItems.remove(menuItem);
		menuItem.setRestaurant(null);
	}

}
