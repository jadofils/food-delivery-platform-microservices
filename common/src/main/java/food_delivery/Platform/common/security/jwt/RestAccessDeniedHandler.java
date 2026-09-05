package food_delivery.Platform.common.security.jwt;

import java.io.IOException;

import org.slf4j.MDC;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import food_delivery.Platform.common.error.ApiErrorResponse;
import food_delivery.Platform.common.error.ForbiddenException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.ObjectMapper;

/**
 * Writes the same {@code ApiErrorResponse} envelope (RULES.md §14) for a request that IS
 * authenticated but lacks the permission a route-level matcher requires — the filter-chain-level
 * counterpart of {@code @ExceptionHandler(AccessDeniedException.class)} in a service's own advice.
 * A {@code @PreAuthorize} denial thrown from inside a controller method is instead caught by
 * {@code AbstractGlobalExceptionHandler}'s consumer, since that exception surfaces inside
 * {@code DispatcherServlet}'s own dispatch, where an {@code @ExceptionHandler} can reach it; this
 * handler only covers denials {@code SecurityFilterChain} route matchers raise before that point.
 * See {@link RestAuthenticationEntryPoint} for the 401 counterpart.
 */
public class RestAccessDeniedHandler implements AccessDeniedHandler {

	private static final ForbiddenException REASON = new ForbiddenException(
			"You do not have permission to perform this action.");

	private final ObjectMapper objectMapper;

	public RestAccessDeniedHandler(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response,
			AccessDeniedException accessDeniedException) throws IOException {
		String traceId = MDC.get("traceId");
		ApiErrorResponse body = ApiErrorResponse.of(REASON, REASON.getMessage(), request.getRequestURI(), traceId);
		response.setStatus(REASON.status());
		response.setContentType("application/json");
		response.getWriter().write(objectMapper.writeValueAsString(body));
	}

}
