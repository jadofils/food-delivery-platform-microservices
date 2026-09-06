package food_delivery.Platform.restaurantservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Completes an FDP restaurant profile for the already-authenticated Keycloak owner calling
 * {@code POST /api/restaurants/me} — the restaurant-onboarding equivalent of
 * {@code customer-service}'s registration endpoint. The owner identity itself comes from the
 * caller's own JWT ({@code sub}), never the request body.
 */
public record RestaurantRegistrationRequest(

		@NotBlank(message = "name is required") @Size(max = 150) String name,

		@Size(max = 1000) String description,

		@NotBlank(message = "cuisineType is required") @Size(max = 50) String cuisineType,

		@NotBlank(message = "street is required") @Size(max = 200) String street,

		@NotBlank(message = "city is required") @Size(max = 100) String city,

		@NotBlank(message = "state is required") @Size(max = 100) String state,

		@NotBlank(message = "postalCode is required") @Size(max = 20) String postalCode,

		@NotBlank(message = "country is required") @Size(max = 100) String country) {
}
