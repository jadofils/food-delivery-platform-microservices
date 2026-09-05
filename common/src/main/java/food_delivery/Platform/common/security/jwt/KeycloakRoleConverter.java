package food_delivery.Platform.common.security.jwt;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Maps a validated Keycloak {@link Jwt}'s {@code resource_access.fdp-api.roles} claim into Spring
 * Security {@link GrantedAuthority}s, so {@code @PreAuthorize("hasAuthority('order:create')")}
 * (RULES.md §8) checks the same permission string Keycloak issued — no {@code ROLE_} prefix, since
 * these are FDP's own action-shaped permission strings, not Spring's role convention. Every
 * service's Resource Server config wires this in identically:
 *
 * <pre>{@code
 * http.oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(
 *         jwtAuthenticationConverter())));
 *
 * @Bean
 * Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter() {
 *     return new KeycloakRoleConverter();
 * }
 * }</pre>
 *
 * See docs/RULES.md §3 — this is exactly the "producer and every consumer must agree on this
 * atomically" case that belongs in {@code common}, not duplicated per service.
 */
public class KeycloakRoleConverter implements Converter<Jwt, AbstractAuthenticationToken> {

	@Override
	public AbstractAuthenticationToken convert(Jwt jwt) {
		List<GrantedAuthority> authorities = JwtClaims.permissions(jwt).stream()
				.map(SimpleGrantedAuthority::new)
				.collect(Collectors.toList());
		return new JwtAuthenticationToken(jwt, authorities);
	}

}
