package food_delivery.Platform.restaurantservice.dto;

import java.time.Instant;

import food_delivery.Platform.restaurantservice.entity.Restaurant;

/**
 * Nothing here is {@code @Masked} — a restaurant's name/address is public storefront information,
 * not the human-readable PII (email, username, phone) RULES.md §8's masking rule targets. {@code id}
 * is a structural identifier, never masked either way.
 */
public record RestaurantResponse(
		Long id,
		String name,
		String description,
		String cuisineType,
		String street,
		String city,
		String state,
		String postalCode,
		String country,
		boolean isOpen,
		Instant createdAt) {

	public static RestaurantResponse from(Restaurant restaurant) {
		return new RestaurantResponse(
				restaurant.getId(),
				restaurant.getName(),
				restaurant.getDescription(),
				restaurant.getCuisineType(),
				restaurant.getStreet(),
				restaurant.getCity(),
				restaurant.getState(),
				restaurant.getPostalCode(),
				restaurant.getCountry(),
				restaurant.isOpen(),
				restaurant.getCreatedAt());
	}

}
