package food_delivery.Platform.common.error;

/**
 * The caller has exceeded a rate limit. {@code api-gateway}'s Redis-backed
 * {@code RequestRateLimiter} (RULES.md §12) generates its own 429 responses directly and doesn't
 * go through this exception — this type is for an individual service enforcing its own
 * additional, narrower limit (e.g. on a single expensive endpoint). Maps to
 * {@code 429 Too Many Requests}. See docs/RULES.md §14.
 */
public class TooManyRequestsException extends ClientErrorException {

	private static final int STATUS = 429;
	private static final String CODE = "TOO_MANY_REQUESTS";

	public TooManyRequestsException(String message) {
		super(message, STATUS, CODE);
	}

	public TooManyRequestsException(String message, Throwable cause) {
		super(message, cause, STATUS, CODE);
	}

}
