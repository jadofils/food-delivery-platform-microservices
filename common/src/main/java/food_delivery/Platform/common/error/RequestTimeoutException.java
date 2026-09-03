package food_delivery.Platform.common.error;

/**
 * The caller didn't complete sending the request in time. Rare in a JSON REST API but kept for
 * completeness of the 4xx range. Maps to {@code 408 Request Timeout}. See docs/RULES.md §14.
 */
public class RequestTimeoutException extends ClientErrorException {

	private static final int STATUS = 408;
	private static final String CODE = "REQUEST_TIMEOUT";

	public RequestTimeoutException(String message) {
		super(message, STATUS, CODE);
	}

	public RequestTimeoutException(String message, Throwable cause) {
		super(message, cause, STATUS, CODE);
	}

}
