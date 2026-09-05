package food_delivery.Platform.customerservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Updates the one profile field this service treats as its own to change post-registration.
 * {@code email}/{@code firstName}/{@code lastName} stay sourced from Keycloak, not editable here —
 * changing those is Keycloak's account-management job, not customer-service's.
 */
public record CustomerUpdateRequest(

		@NotBlank(message = "phoneNumber is required")
		@Pattern(regexp = "^\\+?[0-9]{7,15}$", message = "phoneNumber must be 7-15 digits, optionally starting with +")
		String phoneNumber) {
}
