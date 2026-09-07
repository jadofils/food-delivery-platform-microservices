package food_delivery.Platform.deliveryservice.exception;

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
 * Adds only the {@code AccessDeniedException} handler every {@code @PreAuthorize}-gated service
 * needs (RULES.md §14) — this service makes no outbound sync calls, so it doesn't need the
 * Feign/circuit-breaker handlers {@code order-service}'s equivalent class carries.
 */
@RestControllerAdvice
public class DeliveryServiceExceptionHandler extends AbstractGlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(DeliveryServiceExceptionHandler.class);

	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
		String traceId = MDC.get("traceId");
		log.debug("Access denied on {} [traceId={}]: {}", request.getRequestURI(), traceId, ex.getMessage());
		ForbiddenException reason = new ForbiddenException("You do not have permission to perform this action.");
		ApiErrorResponse body = ApiErrorResponse.of(reason, reason.getMessage(), request.getRequestURI(), traceId);
		return ResponseEntity.status(reason.status()).body(body);
	}

}
