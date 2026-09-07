package food_delivery.Platform.orderservice.client;

import java.util.List;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import food_delivery.Platform.orderservice.client.dto.MenuItemValidationResponse;
import food_delivery.Platform.orderservice.client.dto.RestaurantValidationResponse;

/**
 * See {@link CustomerServiceClient}'s class comment — same ownership/resolution/token-relay
 * reasoning applies here. These call {@code restaurant-service}'s *public browsing* routes
 * (gated by {@code restaurant:menu:read}, which the placing customer's own token already carries)
 * — order-service never needs restaurant-owner-level access to validate an order.
 */
@FeignClient(name = ServiceNames.RESTAURANT_SERVICE)
public interface RestaurantServiceClient {

	@GetMapping("/api/restaurants/{id}")
	RestaurantValidationResponse getRestaurant(@PathVariable("id") Long id);

	@GetMapping("/api/restaurants/{id}/menu-items")
	List<MenuItemValidationResponse> getMenuItems(@PathVariable("id") Long id);

}
