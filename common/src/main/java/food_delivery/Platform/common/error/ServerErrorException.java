package food_delivery.Platform.common.error;

/**
 * A {@link DomainException} whose status is 5xx: this service, or something it depends on, failed
 * to fulfill an otherwise-valid request. Unlike {@link ClientErrorException}, retrying unchanged
 * may succeed once the underlying condition clears — this is the split Resilience4j retry/circuit
 * breaker policies (RULES.md §7) care about, and why
 * {@link food_delivery.Platform.common.error.AbstractGlobalExceptionHandler} logs these at
 * {@code ERROR}: a 5xx means our side of the system, not the caller, needs attention.
 */
public abstract class ServerErrorException extends DomainException {

	protected ServerErrorException(String message, int status, String code) {
		super(message, status, code);
	}

	protected ServerErrorException(String message, Throwable cause, int status, String code) {
		super(message, cause, status, code);
	}

}
