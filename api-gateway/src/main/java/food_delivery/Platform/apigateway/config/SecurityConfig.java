package food_delivery.Platform.apigateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;

import food_delivery.Platform.apigateway.security.RestServerAuthenticationEntryPoint;
import food_delivery.Platform.common.security.jwt.KeycloakRoleConverter;
import tools.jackson.databind.ObjectMapper;

/**
 * Sprint 4's "JWT validation at the edge" (SPRINTS.md; RULES.md §8): every request is
 * authenticated here before a route is even resolved, except {@code /actuator/health}. This is
 * the WebFlux equivalent of every other service's {@code SecurityConfig} — same
 * {@code jwk-set-uri}-over-{@code issuer-uri} reasoning (see {@code order-service}'s own
 * {@code SecurityConfig}), same {@link KeycloakRoleConverter} for reading
 * {@code resource_access.fdp-api.roles} — but built on {@link ServerHttpSecurity}/
 * {@link SecurityWebFilterChain} instead of {@code HttpSecurity}/{@code SecurityFilterChain},
 * since {@code api-gateway} is the one WebFlux service in FDP (RULES.md §14).
 *
 * <p>Deliberately does <b>not</b> add any {@code hasAuthority(...)} check here — Sprint 4's scope
 * is authentication only ("is this caller who they claim to be"), never authorization ("can they
 * do this specific thing"). Fine-grained permission checks stay exactly where they already are,
 * on each downstream service's own {@code @PreAuthorize} — this gateway's validation is a first
 * line of defense, not a replacement for it (downstream services still re-validate the JWT
 * locally, per RULES.md §8's defense-in-depth).
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

	@Value("${fdp.security.jwk-set-uri}")
	private String jwkSetUri;

	@Bean
	public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http, ObjectMapper objectMapper) {
		http
				.csrf(ServerHttpSecurity.CsrfSpec::disable)
				.securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
				.authorizeExchange(exchange -> exchange
						.pathMatchers("/actuator/health").permitAll()
						.anyExchange().authenticated())
				.oauth2ResourceServer(oauth2 -> oauth2
						.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
				.exceptionHandling(ex -> ex
						.authenticationEntryPoint(new RestServerAuthenticationEntryPoint(objectMapper)));
		return http.build();
	}

	@Bean
	public ReactiveJwtDecoder reactiveJwtDecoder() {
		return NimbusReactiveJwtDecoder.withJwkSetUri(jwkSetUri).build();
	}

	/**
	 * Wraps the same {@link KeycloakRoleConverter} every Servlet-stack service already uses —
	 * {@code Converter<Jwt, AbstractAuthenticationToken>} is stack-agnostic by signature, so no new
	 * claim-reading logic is written here; {@link ReactiveJwtAuthenticationConverterAdapter} is
	 * Spring Security's own bridge from that synchronous converter to the
	 * {@code Converter<Jwt, Mono<AbstractAuthenticationToken>>} shape
	 * {@code ServerHttpSecurity}'s resource-server DSL expects.
	 */
	@Bean
	public ReactiveJwtAuthenticationConverterAdapter jwtAuthenticationConverter() {
		return new ReactiveJwtAuthenticationConverterAdapter(new KeycloakRoleConverter());
	}

}
