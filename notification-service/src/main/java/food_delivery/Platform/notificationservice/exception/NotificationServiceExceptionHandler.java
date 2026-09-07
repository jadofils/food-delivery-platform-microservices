package food_delivery.Platform.notificationservice.exception;

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
 * Adds only the {@code AccessDeniedException} handler for {@code @PreAuthorize} denials — this
 * service makes no outbound Feign calls (it's a pure event consumer plus a read-only query API),
 * so unlike {@code order-service} it needs no {@code FeignException}/{@code CallNotPermittedException}
 * handling (RULES.md §14).
 */
@RestControllerAdvice
public class NotificationServiceExceptionHandler extends AbstractGlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(NotificationServiceExceptionHandler.class);

	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
		String traceId = MDC.get("traceId");
		log.debug("Access denied on {} [traceId={}]: {}", request.getRequestURI(), traceId, ex.getMessage());
		ForbiddenException reason = new ForbiddenException("You do not have permission to perform this action.");
		ApiErrorResponse body = ApiErrorResponse.of(reason, reason.getMessage(), request.getRequestURI(), traceId);
		return ResponseEntity.status(reason.status()).body(body);
	}

}
