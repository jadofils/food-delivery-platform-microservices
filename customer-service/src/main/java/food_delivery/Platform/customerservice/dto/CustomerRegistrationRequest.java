package food_delivery.Platform.customerservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Completes an FDP customer profile for the already-authenticated Keycloak user calling
 * {@code POST /api/customers/me}. Deliberately does NOT accept {@code email}/{@code firstName}/
 * {@code lastName} in the body — those come from the caller's own verified JWT claims, never from
 * client-supplied input, since Keycloak (not this service) is the source of truth for identity
 * (RULES.md §8).
 */
public record CustomerRegistrationRequest(

		@NotBlank(message = "phoneNumber is required")
		@Pattern(regexp = "^\\+?[0-9]{7,15}$", message = "phoneNumber must be 7-15 digits, optionally starting with +")
		String phoneNumber) {
}
