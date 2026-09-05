/**
 * Shared Keycloak-JWT plumbing every FDP service's Spring Security OAuth2 Resource Server config
 * needs identically — reading {@code resource_access.fdp-api.roles} the same way, and returning
 * 401/403 in the same {@code ApiErrorResponse} envelope as every other error (RULES.md §14). This
 * is deliberately NOT a token codec: Keycloak signs and issues the token, Spring Security's stock
 * {@code spring-boot-starter-oauth2-resource-server} decodes and verifies it — nothing here
 * duplicates that. See docs/RULES.md §3, §8 and docs/decisions/0001-retire-identity-service-for-keycloak.md.
 *
 * <p>What a service still owns itself, and this package deliberately does NOT provide:
 * <ul>
 * <li>The {@code SecurityFilterChain} bean and its route rules (which paths are
 * {@code permitAll()}, which need {@code @PreAuthorize}) — that's business-shaped per service.</li>
 * <li>The {@code JwtDecoder} bean (issuer-uri / jwk-set-uri config) — service-specific config, not
 * cross-cutting code.</li>
 * </ul>
 */
package food_delivery.Platform.common.security.jwt;
