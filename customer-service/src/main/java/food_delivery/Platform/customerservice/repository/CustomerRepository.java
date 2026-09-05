package food_delivery.Platform.customerservice.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import food_delivery.Platform.customerservice.entity.Customer;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

	Optional<Customer> findByKeycloakId(String keycloakId);

	boolean existsByKeycloakId(String keycloakId);

	/**
	 * The one query that actually needs {@code addresses} loaded — an explicit
	 * {@code @EntityGraph} rather than making the association eager on every {@code Customer}
	 * fetch (RULES.md — "no unnecessary eager loading"; entity graphs used for specific optimized
	 * queries).
	 */
	@EntityGraph(attributePaths = "addresses")
	Optional<Customer> findWithAddressesByKeycloakId(String keycloakId);

}
