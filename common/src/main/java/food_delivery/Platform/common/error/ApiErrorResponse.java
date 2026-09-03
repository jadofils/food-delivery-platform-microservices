package food_delivery.Platform.common.error;

import java.time.Instant;
import java.util.List;

/**
 * The one error response shape every FDP service returns — {@code timestamp}, {@code status},
 * a machine-readable {@code error} code, a human-readable {@code message}, the request
 * {@code path}, the distributed {@code traceId}, and an optional {@code errors} list of
 * {@link FieldError} for validation failures. No service invents its own shape. See
 * docs/RULES.md §14.
 *
 * <p>Built by a service's own {@code @RestControllerAdvice} via the factory methods below —
 * this class supplies the shared shape and the status-mapping helpers, not the advice itself.
 */
public record ApiErrorResponse(
		Instant timestamp,
		int status,
		String error,
		String message,
		String path,
		String traceId,
		List<FieldError> errors) {

	/** One invalid field and why, per §14 — one entry per violation, not one response per violation. */
	public record FieldError(String field, String message) {
	}

	/**
	 * Builds the envelope for a caught {@link DomainException} (or any {@link ApiError}).
	 */
	public static ApiErrorResponse of(ApiError apiError, String message, String path, String traceId) {
		return new ApiErrorResponse(Instant.now(), apiError.status(), apiError.code(), message, path, traceId,
				List.of());
	}

	/**
	 * Builds the envelope for Bean Validation failures ({@code MethodArgumentNotValidException} /
	 * {@code ConstraintViolationException}) — one {@link FieldError} per invalid field. See
	 * docs/RULES.md §15.
	 */
	public static ApiErrorResponse ofValidation(String message, String path, String traceId,
			List<FieldError> fieldErrors) {
		return new ApiErrorResponse(Instant.now(), 400, "VALIDATION_FAILED", message, path, traceId, fieldErrors);
	}

	/**
	 * Builds the sanitized envelope for an unmapped/unexpected exception. Never includes the
	 * underlying exception's message or stack trace in the response — log those server-side
	 * against {@code traceId} instead. See docs/RULES.md §14.
	 */
	public static ApiErrorResponse ofUnexpected(String path, String traceId) {
		return new ApiErrorResponse(Instant.now(), 500, "INTERNAL_ERROR", "An unexpected error occurred.", path,
				traceId, List.of());
	}

}
