package food_delivery.Platform.identityservice.dto;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import food_delivery.Platform.common.security.masking.Masked;

/**
 * {@code permissions} is the flattened union of every role's grants — the shape token issuance
 * mirrors in the JWT's own claims (RULES.md §8), computed here already so the design didn't
 * change when that landed.
 *
 * <p>{@code email} is masked (RULES.md §8) since it's human-readable PII; {@code id} is not — it's
 * the structural identifier this same API uses to address the user in later calls
 * ({@code /api/v1/users/{id}/...}), and masking it would break that addressability without adding
 * real protection.
 */
public record UserResponse(
		UUID id,
		@Masked String email,
		boolean enabled,
		boolean accountNonLocked,
		Set<String> roles,
		Set<String> permissions,
		Instant createdAt) {
}
