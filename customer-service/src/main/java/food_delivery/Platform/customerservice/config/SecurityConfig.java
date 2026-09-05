package food_delivery.Platform.customerservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.core.convert.converter.Converter;

import food_delivery.Platform.common.security.jwt.KeycloakRoleConverter;
import food_delivery.Platform.common.security.jwt.RestAccessDeniedHandler;
import food_delivery.Platform.common.security.jwt.RestAuthenticationEntryPoint;
import tools.jackson.databind.ObjectMapper;

/**
 * Every endpoint requires a valid Keycloak-issued token by default (RULES.md §8 — "secure all
 * endpoints"); the only exceptions are Swagger UI/OpenAPI docs and the Actuator health check,
 * neither of which carries FDP data. Stateless REST API: no HTTP session, no CSRF token (there's
 * no cookie-based auth here for CSRF to matter against).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

	/**
	 * {@code jwk-set-uri}, not {@code issuer-uri}: builds a {@link JwtDecoder} that only fetches
	 * Keycloak's public keys lazily, on first token verification, rather than performing an eager
	 * {@code /.well-known/openid-configuration} discovery call while this bean is constructed —
	 * this service already knows its realm's shape statically, so OIDC discovery buys nothing and
	 * would otherwise make the application context fail to start whenever Keycloak isn't reachable
	 * yet at boot (a real risk under docker-compose startup ordering, and in tests).
	 */
	@Value("${fdp.security.jwk-set-uri}")
	private String jwkSetUri;

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper) throws Exception {
		http
				.csrf(csrf -> csrf.disable())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/actuator/health").permitAll()
						.anyRequest().authenticated())
				.oauth2ResourceServer(oauth2 -> oauth2
						.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
				.exceptionHandling(ex -> ex
						.authenticationEntryPoint(new RestAuthenticationEntryPoint(objectMapper))
						.accessDeniedHandler(new RestAccessDeniedHandler(objectMapper)));
		return http.build();
	}

	@Bean
	public JwtDecoder jwtDecoder() {
		return NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
	}

	@Bean
	public Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter() {
		return new KeycloakRoleConverter();
	}

}
