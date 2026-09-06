package food_delivery.Platform.restaurantservice.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import food_delivery.Platform.restaurantservice.entity.Restaurant;

public interface RestaurantRepository extends JpaRepository<Restaurant, Long> {

	Optional<Restaurant> findByOwnerKeycloakId(String ownerKeycloakId);

	boolean existsByOwnerKeycloakId(String ownerKeycloakId);

	/**
	 * The one query that actually needs {@code menuItems} loaded — an explicit
	 * {@code @EntityGraph} rather than making the association eager on every {@code Restaurant}
	 * fetch (RULES.md — "no unnecessary eager loading"; entity graphs used for specific optimized
	 * queries).
	 */
	@EntityGraph(attributePaths = "menuItems")
	Optional<Restaurant> findWithMenuItemsById(Long id);

}
