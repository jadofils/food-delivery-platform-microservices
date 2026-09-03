package food_delivery.Platform.identityservice.dto;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * {@code permissions} is the flattened union of every role's grants — the shape a future JWT's
 * claims will mirror once token issuance lands (RULES.md §8), computed here already so that
 * design doesn't change when it does.
 */
public record UserResponse(
		UUID id,
		String email,
		boolean enabled,
		boolean accountNonLocked,
		Set<String> roles,
		Set<String> permissions,
		Instant createdAt) {
}
