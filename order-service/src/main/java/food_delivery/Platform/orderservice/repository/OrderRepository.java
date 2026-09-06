package food_delivery.Platform.orderservice.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import food_delivery.Platform.orderservice.entity.Order;

public interface OrderRepository extends JpaRepository<Order, Long> {

	Page<Order> findByCustomerKeycloakId(String customerKeycloakId, Pageable pageable);

	/** Ownership check baked into the query itself — never trust a path id alone (RULES.md §8). */
	Optional<Order> findByIdAndCustomerKeycloakId(Long id, String customerKeycloakId);

	/**
	 * The one query that needs {@code items} loaded — an explicit {@code @EntityGraph} rather
	 * than making the association eager on every {@code Order} fetch (RULES.md — "no unnecessary
	 * eager loading").
	 */
	@EntityGraph(attributePaths = "items")
	Optional<Order> findWithItemsByIdAndCustomerKeycloakId(Long id, String customerKeycloakId);

}
