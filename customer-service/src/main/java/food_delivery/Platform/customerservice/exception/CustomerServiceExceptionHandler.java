package food_delivery.Platform.customerservice.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import food_delivery.Platform.common.error.AbstractGlobalExceptionHandler;
import food_delivery.Platform.common.error.ApiErrorResponse;
import food_delivery.Platform.common.error.ForbiddenException;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Adds one handler beyond the shared base (RULES.md §14): a {@code @PreAuthorize} denial
 * ({@link AccessDeniedException}) thrown from inside a controller method surfaces here, inside
 * {@code DispatcherServlet}'s own dispatch — see
 * {@code common.security.jwt.RestAccessDeniedHandler} for the filter-chain-level counterpart that
 * covers denials {@code SecurityFilterChain} route matchers raise before a request ever reaches a
 * controller. {@code customer-service} makes no outbound Feign/HTTP calls yet, so no
 * {@code FeignException}/{@code CallNotPermittedException} handler is needed here (RULES.md §14 —
 * added only once this service actually calls another one, Sprint 3+).
 */
@RestControllerAdvice
public class CustomerServiceExceptionHandler extends AbstractGlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(CustomerServiceExceptionHandler.class);

	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
		String traceId = MDC.get("traceId");
		log.debug("Access denied on {} [traceId={}]: {}", request.getRequestURI(), traceId, ex.getMessage());
		ForbiddenException reason = new ForbiddenException("You do not have permission to perform this action.");
		ApiErrorResponse body = ApiErrorResponse.of(reason, reason.getMessage(), request.getRequestURI(), traceId);
		return ResponseEntity.status(reason.status()).body(body);
	}

}
