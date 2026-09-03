package food_delivery.Platform.common.error;

/**
 * Base type for every unchecked, business-rule-carrying exception raised from a service's
 * business/service layer. Unchecked by design: Spring's exception translation,
 * {@code @Transactional}'s rollback-on-unchecked default, and {@code @RestControllerAdvice} are
 * all built around unchecked propagation. Checked exceptions are reserved narrowly for cases
 * where forcing the caller to acknowledge failure at compile time earns its keep (e.g. a Feign
 * fallback method's declared failure mode) — see docs/RULES.md §14.
 */
public abstract class DomainException extends RuntimeException implements ApiError {

	protected DomainException(String message) {
		super(message);
	}

	protected DomainException(String message, Throwable cause) {
		super(message, cause);
	}

}
