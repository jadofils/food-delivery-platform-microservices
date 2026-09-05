package food_delivery.Platform.common.security.jwt;

import java.io.IOException;

import org.slf4j.MDC;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import food_delivery.Platform.common.error.ApiErrorResponse;
import food_delivery.Platform.common.error.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.ObjectMapper;

/**
 * Writes the same {@code ApiErrorResponse} envelope (RULES.md §14) every other error uses, for the
 * one failure mode that never reaches a service's {@code @RestControllerAdvice}: a request with no
 * token, or one that fails signature/expiry validation, is rejected by Spring Security's
 * {@code BearerTokenAuthenticationFilter} — a servlet filter running before {@code DispatcherServlet}
 * — never by a controller method an advice bean could intercept. Without this, that case would fall
 * through to Spring Security's own default entry point and produce a bare, unshaped 401.
 *
 * <p>A service wires this into its {@code SecurityFilterChain}:
 * {@code http.exceptionHandling(ex -> ex.authenticationEntryPoint(new RestAuthenticationEntryPoint(objectMapper)))}.
 * See {@link RestAccessDeniedHandler} for the 403 counterpart.
 */
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

	private static final UnauthorizedException REASON = new UnauthorizedException(
			"Missing or invalid authentication token.");

	private final ObjectMapper objectMapper;

	public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException authException) throws IOException {
		String traceId = MDC.get("traceId");
		ApiErrorResponse body = ApiErrorResponse.of(REASON, REASON.getMessage(), request.getRequestURI(), traceId);
		response.setStatus(REASON.status());
		response.setContentType("application/json");
		response.getWriter().write(objectMapper.writeValueAsString(body));
	}

}
