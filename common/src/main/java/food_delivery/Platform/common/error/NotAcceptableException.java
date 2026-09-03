package food_delivery.Platform.common.error;

/**
 * The client's {@code Accept} header can't be satisfied by anything this endpoint can produce.
 * Maps to {@code 406 Not Acceptable}. See docs/RULES.md §14.
 */
public class NotAcceptableException extends ClientErrorException {

	private static final int STATUS = 406;
	private static final String CODE = "NOT_ACCEPTABLE";

	public NotAcceptableException(String message) {
		super(message, STATUS, CODE);
	}

	public NotAcceptableException(String message, Throwable cause) {
		super(message, cause, STATUS, CODE);
	}

}
