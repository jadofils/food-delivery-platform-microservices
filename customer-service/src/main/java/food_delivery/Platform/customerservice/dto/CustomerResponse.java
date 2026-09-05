package food_delivery.Platform.customerservice.dto;

import java.time.Instant;

import food_delivery.Platform.common.security.masking.Masked;
import food_delivery.Platform.customerservice.entity.Customer;

/**
 * {@code id} is a structural identifier a client addresses this resource with — never masked
 * (RULES.md §8). {@code email}/{@code phoneNumber} are human-readable PII — masked on the way out
 * to JSON via {@link Masked} regardless of who's asking, including the customer looking at their
 * own profile, which is the rule as written (RULES.md §8) and kept consistent rather than adding
 * a "mask unless it's you" exception nowhere else in FDP has.
 */
public record CustomerResponse(
		Long id,
		@Masked String email,
		@Masked String phoneNumber,
		String firstName,
		String lastName,
		Instant createdAt) {

	public static CustomerResponse from(Customer customer) {
		return new CustomerResponse(
				customer.getId(),
				customer.getEmail(),
				customer.getPhoneNumber(),
				customer.getFirstName(),
				customer.getLastName(),
				customer.getCreatedAt());
	}

}
