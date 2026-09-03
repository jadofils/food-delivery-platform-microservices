package food_delivery.Platform.common.security.jwt;

import java.io.IOException;

import food_delivery.Platform.common.error.ApiErrorResponse;
import food_delivery.Platform.common.error.UnauthorizedException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.ObjectMapper;

/**
 * The servlet filter-chain link every FDP service (except {@code api-gateway}, WebFlux) registers
 * to read an incoming {@code Authorization: Bearer <token>} header. If present and valid, the
 * decoded {@link JwtClaims} are attached to the request under {@link #REQUEST_ATTRIBUTE} for
 * {@link PermissionInterceptor} to enforce. A missing header is not rejected here — plenty of
 * endpoints are {@link Public} — but a header that IS present and invalid is rejected immediately
 * with 401, in the same {@link ApiErrorResponse} shape every other error uses (RULES.md §14),
 * built by hand here since a servlet filter runs before Spring MVC's {@code @RestControllerAdvice}
 * ever sees the request.
 *
 * <p>Deliberately a plain {@code jakarta.servlet.Filter}, not a Spring Security filter — no FDP
 * service depends on {@code spring-boot-starter-security} yet. Wrapping this into a proper
 * {@code SecurityFilterChain}/{@code Authentication} is the natural next step if a service later
 * needs something Spring Security provides that this lighter approach doesn't.
 */
public class JwtAuthenticationFilter extends HttpFilter {

	public static final String REQUEST_ATTRIBUTE = "food_delivery.Platform.jwtClaims";

	private static final String BEARER_PREFIX = "Bearer ";

	private final JwtDecoder jwtDecoder;
	private final ObjectMapper objectMapper;

	public JwtAuthenticationFilter(JwtDecoder jwtDecoder, ObjectMapper objectMapper) {
		this.jwtDecoder = jwtDecoder;
		this.objectMapper = objectMapper;
	}

	@Override
	protected void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws IOException, ServletException {
		String header = request.getHeader("Authorization");
		if (header == null || !header.startsWith(BEARER_PREFIX)) {
			chain.doFilter(request, response);
			return;
		}

		try {
			JwtClaims claims = jwtDecoder.decode(header.substring(BEARER_PREFIX.length()));
			request.setAttribute(REQUEST_ATTRIBUTE, claims);
			chain.doFilter(request, response);
		} catch (UnauthorizedException e) {
			writeUnauthorized(request, response, e);
		}
	}

	private void writeUnauthorized(HttpServletRequest request, HttpServletResponse response,
			UnauthorizedException e) throws IOException {
		response.setStatus(e.status());
		response.setContentType("application/json");
		ApiErrorResponse body = ApiErrorResponse.of(e, e.getMessage(), request.getRequestURI(), null);
		objectMapper.writeValue(response.getWriter(), body);
	}

}
