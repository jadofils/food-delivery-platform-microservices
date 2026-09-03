package food_delivery.Platform.common.security.jwt;

import java.time.Instant;
import java.util.Set;

/**
 * The claim shape every FDP token carries — nothing more. Deliberately excludes email or any
 * other display-oriented PII: a token exists to answer "who is this and what can they do," and
 * {@code subject} (the user id) plus {@code permissions} already answer that. See docs/RULES.md
 * §8.
 */
public record JwtClaims(String subject, Set<String> permissions, Instant issuedAt, Instant expiresAt) {
}
