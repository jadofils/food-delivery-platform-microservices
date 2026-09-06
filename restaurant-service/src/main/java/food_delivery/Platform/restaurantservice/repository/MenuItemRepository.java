package food_delivery.Platform.restaurantservice.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import food_delivery.Platform.restaurantservice.entity.MenuItem;

public interface MenuItemRepository extends JpaRepository<MenuItem, Long> {

	List<MenuItem> findByRestaurantId(Long restaurantId);

	/** Ownership check baked into the query itself — never trust a path id alone (RULES.md §8). */
	Optional<MenuItem> findByIdAndRestaurantId(Long id, Long restaurantId);

}
