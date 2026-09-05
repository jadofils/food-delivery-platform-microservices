package food_delivery.Platform.customerservice.entity;

import java.time.Instant;

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
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A delivery address owned by exactly one {@link Customer}. {@code customer} is
 * {@link FetchType#LAZY} — reading an address never needs to pull its parent customer along with
 * it (RULES.md — "no unnecessary eager loading").
 */
@Entity
@Table(name = "addresses")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class Address {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "customer_id", nullable = false)
	private Customer customer;

	/** A human label for this address, e.g. "Home", "Work" — not validated against a fixed set. */
	@Column(nullable = false)
	private String label;

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

	@Column(name = "is_default", nullable = false)
	private boolean isDefault;

	@CreatedDate
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@LastModifiedDate
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	public Address(String label, String street, String city, String state, String postalCode, String country,
			boolean isDefault) {
		this.label = label;
		this.street = street;
		this.city = city;
		this.state = state;
		this.postalCode = postalCode;
		this.country = country;
		this.isDefault = isDefault;
	}

}
