package food_delivery.Platform.common.error;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import food_delivery.Platform.common.error.ApiErrorResponse.FieldError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

/**
 * Shared {@code @ExceptionHandler} methods for the Servlet (Spring MVC) stack used by every FDP
 * service except {@code api-gateway} — which is WebFlux and defines its own
 * {@code ServerWebExchange}-flavored advice, reusing the same {@link ApiError}/
 * {@link DomainException}/{@link ApiErrorResponse} types from this package. See docs/RULES.md
 * §14 and §3.
 *
 * <p>A service wires this in by extending it from a concrete, {@code @RestControllerAdvice}
 * -annotated class:
 *
 * <pre>{@code
 * @RestControllerAdvice
 * public class GlobalExceptionHandler extends AbstractGlobalExceptionHandler {
 *     // Add only what THIS service needs beyond the shared base — e.g. a service that makes
 *     // Feign calls adds handlers for FeignException / CallNotPermittedException here, not in
 *     // the shared base, since not every service makes outbound calls. See RULES.md §14, §7.
 * }
 * }</pre>
 *
 * <h2>Ordering</h2>
 *
 * <p>Two different things are called "ordering" for exception handling, and only one of them
 * needs code:
 *
 * <ul>
 * <li><b>Dispatch order within one advice bean</b> is automatic — Spring's
 * {@code ExceptionHandlerMethodResolver} always picks the most specific declared handler for the
 * thrown exception's actual type (via {@code ExceptionDepthComparator}), regardless of the order
 * methods appear in this file. Nothing to configure. The methods below are still written
 * most-specific to least-specific, ending with the {@link Exception} catch-all, purely so a human
 * reading top to bottom sees the same precedence Spring already applies.</li>
 * <li><b>Precedence across two different advice beans</b> (e.g. if a service ever adds a second,
 * separate {@code @RestControllerAdvice} alongside the one extending this class) is NOT automatic
 * and needs {@code @Order} on each advice class — lower value wins. That situation doesn't exist
 * yet anywhere in FDP (one advice bean per service, inheriting everything from here), so no
 * {@code @Order} is applied here. If it ever does, put {@code @Order} on the concrete advice
 * classes, not in this shared base.</li>
 * </ul>
 */
public abstract class AbstractGlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(AbstractGlobalExceptionHandler.class);

	/**
	 * Most specific: a business/service-layer failure this codebase raised on purpose. Logged at
	 * {@code ERROR} for a {@link ServerErrorException} (our side needs attention) and at
	 * {@code DEBUG} for a {@link ClientErrorException} (expected, frequent, not actionable
	 * operationally — logging every 404 as an error would drown out the logs that matter).
	 */
	@ExceptionHandler(DomainException.class)
	public ResponseEntity<ApiErrorResponse> handleDomainException(DomainException ex, HttpServletRequest request) {
		String traceId = traceId();
		if (ex instanceof ServerErrorException) {
			log.error("Domain exception [{}] on {} [traceId={}]", ex.code(), request.getRequestURI(), traceId, ex);
		} else {
			log.debug("Domain exception [{}] on {} [traceId={}]: {}", ex.code(), request.getRequestURI(), traceId,
					ex.getMessage());
		}
		ApiErrorResponse body = ApiErrorResponse.of(ex, ex.getMessage(), request.getRequestURI(), traceId);
		return ResponseEntity.status(ex.status()).body(body);
	}

	/** A {@code @Valid} {@code @RequestBody} failed Bean Validation. See RULES.md §15. */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex,
			HttpServletRequest request) {
		List<FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
				.map(fe -> new FieldError(fe.getField(), fe.getDefaultMessage()))
				.toList();
		ApiErrorResponse body = ApiErrorResponse.ofValidation("Validation failed.", request.getRequestURI(),
				traceId(), fieldErrors);
		return ResponseEntity.status(body.status()).body(body);
	}

	/** A {@code @Validated} {@code @RequestParam}/{@code @PathVariable} failed. See RULES.md §15. */
	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException ex,
			HttpServletRequest request) {
		List<FieldError> fieldErrors = ex.getConstraintViolations().stream()
				.map(cv -> new FieldError(cv.getPropertyPath().toString(), cv.getMessage()))
				.toList();
		ApiErrorResponse body = ApiErrorResponse.ofValidation("Validation failed.", request.getRequestURI(),
				traceId(), fieldErrors);
		return ResponseEntity.status(body.status()).body(body);
	}

	/**
	 * A path variable or request parameter couldn't be converted to the type the handler method
	 * declares — e.g. {@code GET /api/customers/not-a-number} against a {@code @PathVariable Long
	 * id}. Without this handler the conversion failure propagates as an unmapped exception and
	 * gets reported as a {@code 500}, which is wrong: the caller sent a malformed request, this
	 * service did nothing wrong. Maps to the same {@code VALIDATION_FAILED} shape as a Bean
	 * Validation failure (RULES.md §15) since it's the same category of problem — a single bad
	 * input value — even though Spring raises it before Bean Validation ever runs.
	 */
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ApiErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
			HttpServletRequest request) {
		String traceId = traceId();
		String requiredType = ex.getRequiredType() == null ? "the expected type" : ex.getRequiredType().getSimpleName();
		FieldError fieldError = new FieldError(ex.getName(), "must be a valid " + requiredType);
		ApiErrorResponse body = ApiErrorResponse.ofValidation("Validation failed.", request.getRequestURI(), traceId,
				List.of(fieldError));
		return ResponseEntity.status(body.status()).body(body);
	}

	/**
	 * Least specific, and must stay last: anything not already handled above. Never returns the
	 * original exception's message — that's logged server-side against {@code traceId} instead.
	 * See RULES.md §14.
	 */
	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
		String traceId = traceId();
		log.error("Unhandled exception on {} [traceId={}]", request.getRequestURI(), traceId, ex);
		ApiErrorResponse body = ApiErrorResponse.ofUnexpected(request.getRequestURI(), traceId);
		return ResponseEntity.status(body.status()).body(body);
	}

	/**
	 * The active distributed trace ID, once tracing exists — {@code null} until then. Micrometer
	 * Tracing populates the {@code "traceId"} MDC key automatically when a service adopts it in
	 * Sprint 6 (RULES.md §13); nothing here needs to change when that happens.
	 */
	private static String traceId() {
		return MDC.get("traceId");
	}

}
