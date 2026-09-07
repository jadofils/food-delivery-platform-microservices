package food_delivery.Platform.apigateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import food_delivery.Platform.common.security.jwt.JwtClaims;
import reactor.core.publisher.Mono;

/**
 * Backs the order-placement route's {@code RequestRateLimiter} filter
 * ({@code application.properties}' {@code args.key-resolver=#{@rateLimitKeyResolver}}, RULES.md
 * §12, SPRINTS.md Sprint 4). Keys by the caller's own JWT subject — the same identifier every
 * service's ownership checks already correlate against ({@link JwtClaims#subject}) — so the limit
 * is per customer, not per shared egress IP (several customers behind one NAT/proxy would
 * otherwise share a single bucket). {@code gateway:rate-limit:{clientId}}'s {@code clientId} in
 * RULES.md §12's example key is exactly this subject.
 *
 * <p>Every request reaching this route is already authenticated by {@link SecurityConfig} before
 * routing happens, so {@code exchange.getPrincipal()} always resolves to a
 * {@link JwtAuthenticationToken} in practice; the {@code "anonymous"} fallback only guards the
 * type-mismatch case defensively; it is never the expected path.
 */
@Configuration
public class RateLimiterConfig {

	@Bean
	public KeyResolver rateLimitKeyResolver() {
		return exchange -> exchange.getPrincipal()
				.ofType(JwtAuthenticationToken.class)
				.map(auth -> JwtClaims.subject(auth.getToken()))
				.defaultIfEmpty("anonymous");
	}

}
