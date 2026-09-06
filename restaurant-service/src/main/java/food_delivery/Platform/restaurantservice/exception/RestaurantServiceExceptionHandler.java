package food_delivery.Platform.restaurantservice.exception;

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
 * Same shape as {@code customer-service}'s advice — adds only the
 * {@link AccessDeniedException} handler for {@code @PreAuthorize} denials thrown inside a
 * controller method (RULES.md §14). No outbound Feign/HTTP calls from this service yet, so no
 * {@code FeignException}/{@code CallNotPermittedException} handler is needed here either.
 */
@RestControllerAdvice
public class RestaurantServiceExceptionHandler extends AbstractGlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(RestaurantServiceExceptionHandler.class);

	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
		String traceId = MDC.get("traceId");
		log.debug("Access denied on {} [traceId={}]: {}", request.getRequestURI(), traceId, ex.getMessage());
		ForbiddenException reason = new ForbiddenException("You do not have permission to perform this action.");
		ApiErrorResponse body = ApiErrorResponse.of(reason, reason.getMessage(), request.getRequestURI(), traceId);
		return ResponseEntity.status(reason.status()).body(body);
	}

}
