package food_delivery.Platform.apigateway.security;

import java.nio.charset.StandardCharsets;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.web.server.ServerWebExchange;

import food_delivery.Platform.common.error.ApiErrorResponse;
import food_delivery.Platform.common.error.UnauthorizedException;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

/**
 * The WebFlux/{@code ServerWebExchange} counterpart of {@code common}'s own
 * {@code RestAuthenticationEntryPoint} (Servlet-typed, unusable here) — writes the same {@link
 * ApiErrorResponse} envelope (RULES.md §14) for the one failure mode a
 * {@code @RestControllerAdvice}-style handler never sees: a request with no token, or one that
 * fails signature/expiry validation, is rejected by Spring Security's reactive filter chain before
 * routing ever happens. This is exactly Sprint 4's exit criterion — "unauthenticated or
 * malformed-token requests are rejected at the edge."
 *
 * <p>{@code traceId} is always {@code null} here, not "not yet wired" by omission: this module has
 * no distributed-tracing dependency yet (RULES.md §13 hasn't reached {@code api-gateway}), and even
 * once it does, Reactor's non-thread-bound execution model means a plain {@code MDC.get(...)} read
 * (which every Servlet-stack service uses) would not reliably return the right value here without
 * Reactor Context-to-MDC bridging set up first — a real difference from the Servlet stack, not an
 * oversight to silently paper over with a value that would sometimes be right by accident.
 */
public class RestServerAuthenticationEntryPoint implements ServerAuthenticationEntryPoint {

	private static final UnauthorizedException REASON = new UnauthorizedException(
			"Missing or invalid authentication token.");

	private final ObjectMapper objectMapper;

	public RestServerAuthenticationEntryPoint(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	@Override
	public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException ex) {
		ApiErrorResponse body = ApiErrorResponse.of(REASON, REASON.getMessage(),
				exchange.getRequest().getPath().value(), null);
		byte[] bytes = objectMapper.writeValueAsString(body).getBytes(StandardCharsets.UTF_8);

		ServerHttpResponse response = exchange.getResponse();
		response.setStatusCode(HttpStatus.valueOf(REASON.status()));
		response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
		DataBuffer buffer = response.bufferFactory().wrap(bytes);
		return response.writeWith(Mono.just(buffer));
	}

}
