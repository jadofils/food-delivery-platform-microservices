package food_delivery.Platform.orderservice.exception;

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
import food_delivery.Platform.common.error.ServiceUnavailableException;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import jakarta.servlet.http.HttpServletRequest;

/**
 * order-service is the first FDP service that makes outbound Feign calls, so it's the first to
 * need the {@code FeignException}/{@code CallNotPermittedException} handlers RULES.md §14
 * anticipated living per-service, not in the shared base (not every service calls out to another
 * one). Both map to the same {@link ServiceUnavailableException} shape a Resilience4j fallback
 * method already throws (see {@code CustomerServiceGateway}) — this handler is a defensive second
 * line, not the primary path: in normal operation the circuit breaker's fallback method already
 * translates these before they would ever reach here.
 */
@RestControllerAdvice
public class OrderServiceExceptionHandler extends AbstractGlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(OrderServiceExceptionHandler.class);

	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
		String traceId = MDC.get("traceId");
		log.debug("Access denied on {} [traceId={}]: {}", request.getRequestURI(), traceId, ex.getMessage());
		ForbiddenException reason = new ForbiddenException("You do not have permission to perform this action.");
		ApiErrorResponse body = ApiErrorResponse.of(reason, reason.getMessage(), request.getRequestURI(), traceId);
		return ResponseEntity.status(reason.status()).body(body);
	}

	/** The circuit breaker for a downstream client is {@code OPEN} — it's failing fast, not calling out at all. */
	@ExceptionHandler(CallNotPermittedException.class)
	public ResponseEntity<ApiErrorResponse> handleCallNotPermitted(CallNotPermittedException ex,
			HttpServletRequest request) {
		String traceId = MDC.get("traceId");
		log.error("Circuit breaker OPEN on {} [traceId={}]: {}", request.getRequestURI(), traceId, ex.getMessage());
		ServiceUnavailableException reason = new ServiceUnavailableException(
				"A downstream service is currently unavailable. Please try again shortly.", ex);
		ApiErrorResponse body = ApiErrorResponse.of(reason, reason.getMessage(), request.getRequestURI(), traceId);
		return ResponseEntity.status(reason.status()).body(body);
	}

	/** A raw, untranslated Feign failure that escaped a gateway's own try/catch — see class javadoc. */
	@ExceptionHandler(FeignException.class)
	public ResponseEntity<ApiErrorResponse> handleFeignException(FeignException ex, HttpServletRequest request) {
		String traceId = MDC.get("traceId");
		log.error("Unhandled Feign failure on {} [traceId={}]: {}", request.getRequestURI(), traceId, ex.getMessage());
		ServiceUnavailableException reason = new ServiceUnavailableException(
				"A downstream service is currently unavailable. Please try again shortly.", ex);
		ApiErrorResponse body = ApiErrorResponse.of(reason, reason.getMessage(), request.getRequestURI(), traceId);
		return ResponseEntity.status(reason.status()).body(body);
	}

}
