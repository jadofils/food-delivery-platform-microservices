package food_delivery.Platform.common.error;

/**
 * The request conflicts with the resource's current state (e.g. a duplicate registration). Maps
 * to {@code 409 Conflict}. See docs/RULES.md §14.
 */
public class ConflictException extends ClientErrorException {

	private static final int STATUS = 409;
	private static final String CODE = "CONFLICT";

	public ConflictException(String message) {
		super(message, STATUS, CODE);
	}

	public ConflictException(String message, Throwable cause) {
		super(message, cause, STATUS, CODE);
	}

}
