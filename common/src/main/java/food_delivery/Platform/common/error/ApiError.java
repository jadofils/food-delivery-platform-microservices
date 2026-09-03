package food_delivery.Platform.common.error;

/**
 * Contract every {@link DomainException} subtype implements: its HTTP status and a stable,
 * machine-readable error code. A service's {@code @RestControllerAdvice} maps any
 * {@code ApiError} generically instead of hardcoding a growing if/else chain. See
 * docs/RULES.md §14.
 *
 * <p>Deliberately returns a plain {@code int} rather than Spring Web's {@code HttpStatus} so
 * this module — and anything that depends only on the exception hierarchy — has no Spring Web
 * dependency. A service's own advice, which already depends on Spring Web, converts trivially
 * via {@code ResponseEntity.status(apiError.status())}.
 */
public interface ApiError {

	int status();

	String code();

}
