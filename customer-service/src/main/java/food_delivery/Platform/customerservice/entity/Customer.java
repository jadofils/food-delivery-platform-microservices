package food_delivery.Platform.customerservice.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

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
 * A customer's FDP profile. Keyed to Keycloak by {@link #keycloakId} ({@code sub} claim, RULES.md
 * §8) — this service never stores a password or any credential; Keycloak owns that entirely.
 * {@code email}/{@code firstName}/{@code lastName} are a local copy taken from the token at
 * registration time (not re-synced live), since {@code customer-service} needs to serve profile
 * reads without calling Keycloak per request.
 *
 * <p>{@code addresses} is {@link FetchType#LAZY} on purpose (RULES.md — "no unnecessary eager
 * loading"): most operations on a {@code Customer} don't need its addresses, and
 * {@code spring.jpa.open-in-view=false} (see {@code application.properties}) means a controller
 * that DOES need them must ask for them explicitly — see
 * {@code CustomerRepository#findWithAddressesById}, which uses an {@code @EntityGraph} for exactly
 * that one query instead of making the association eager everywhere.
 */
@Entity
@Table(name = "customers")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class Customer {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "keycloak_id", nullable = false, unique = true, updatable = false)
	private String keycloakId;

	@Column(nullable = false)
	private String email;

	@Column(name = "phone_number", nullable = false)
	private String phoneNumber;

	@Column(name = "first_name")
	private String firstName;

	@Column(name = "last_name")
	private String lastName;

	@OneToMany(mappedBy = "customer", fetch = FetchType.LAZY, cascade = jakarta.persistence.CascadeType.ALL,
			orphanRemoval = true)
	private List<Address> addresses = new ArrayList<>();

	/** Optimistic locking — cheap insurance against a lost update on concurrent profile edits. */
	@Version
	private Long version;

	@CreatedDate
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@LastModifiedDate
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	public Customer(String keycloakId, String email, String phoneNumber, String firstName, String lastName) {
		this.keycloakId = keycloakId;
		this.email = email;
		this.phoneNumber = phoneNumber;
		this.firstName = firstName;
		this.lastName = lastName;
	}

	public void addAddress(Address address) {
		addresses.add(address);
		address.setCustomer(this);
	}

	public void removeAddress(Address address) {
		addresses.remove(address);
		address.setCustomer(null);
	}

}
