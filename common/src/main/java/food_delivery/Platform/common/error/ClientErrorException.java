package food_delivery.Platform.common.error;

/**
 * A {@link DomainException} whose status is 4xx: the request itself is the problem — malformed,
 * unauthenticated, unauthorized, referencing something that doesn't exist, or otherwise not
 * something the caller can fix by retrying unchanged. Not, on its own, evidence that anything on
 * this service is broken — {@link food_delivery.Platform.common.error.AbstractGlobalExceptionHandler}
 * logs these at a lower level than {@link ServerErrorException} for exactly that reason.
 */
public abstract class ClientErrorException extends DomainException {

	protected ClientErrorException(String message, int status, String code) {
		super(message, status, code);
	}

	protected ClientErrorException(String message, Throwable cause, int status, String code) {
		super(message, cause, status, code);
	}

}
