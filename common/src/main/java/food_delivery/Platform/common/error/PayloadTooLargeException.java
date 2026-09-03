package food_delivery.Platform.common.error;

/**
 * The request body exceeds a limit this endpoint enforces. Maps to
 * {@code 413 Payload Too Large}. See docs/RULES.md §14.
 */
public class PayloadTooLargeException extends ClientErrorException {

	private static final int STATUS = 413;
	private static final String CODE = "PAYLOAD_TOO_LARGE";

	public PayloadTooLargeException(String message) {
		super(message, STATUS, CODE);
	}

	public PayloadTooLargeException(String message, Throwable cause) {
		super(message, cause, STATUS, CODE);
	}

}
