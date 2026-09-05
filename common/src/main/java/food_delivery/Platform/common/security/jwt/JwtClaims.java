package food_delivery.Platform.common.security.jwt;

import java.util.List;
import java.util.Map;

import org.springframework.security.oauth2.jwt.Jwt;

/**
 * One correct, shared reading of Keycloak's access-token claim shape — every service that reads
 * "who is this" or "what can they do" from a {@link Jwt} does it through here, so the claim path
 * is never re-typed (and never silently drifts) per service. See docs/RULES.md §3, §8.
 */
public final class JwtClaims {

	/**
	 * The single client every FDP permission is modeled on (RULES.md §8) — one client for the
	 * whole platform, not one per service, so this is a constant, not per-service config.
	 */
	private static final String CLIENT_ID = "fdp-api";

	private JwtClaims() {
	}

	/** Keycloak's stable user identifier ({@code sub}) — what a service correlates its own rows to. */
	public static String subject(Jwt jwt) {
		return jwt.getSubject();
	}

	public static String email(Jwt jwt) {
		return jwt.getClaimAsString("email");
	}

	public static String firstName(Jwt jwt) {
		return jwt.getClaimAsString("given_name");
	}

	public static String lastName(Jwt jwt) {
		return jwt.getClaimAsString("family_name");
	}

	/**
	 * FDP's permission strings ({@code order:create}, {@code user:read}, …) for this token, read
	 * from {@code resource_access.fdp-api.roles} — Keycloak's default embedding of a client's
	 * roles, no custom protocol mapper needed (RULES.md §8). Empty, never {@code null}, if the
	 * claim or the {@code fdp-api} entry is absent.
	 */
	@SuppressWarnings("unchecked")
	public static List<String> permissions(Jwt jwt) {
		Map<String, Object> resourceAccess = jwt.getClaimAsMap("resource_access");
		if (resourceAccess == null) {
			return List.of();
		}
		Object clientEntry = resourceAccess.get(CLIENT_ID);
		if (!(clientEntry instanceof Map<?, ?> clientMap)) {
			return List.of();
		}
		Object roles = clientMap.get("roles");
		if (!(roles instanceof List<?> roleList)) {
			return List.of();
		}
		return (List<String>) (List<?>) roleList;
	}

}
