package food_delivery.Platform.common.error;

/**
 * The request is malformed in a way Bean Validation doesn't already catch (see
 * {@code ApiErrorResponse#ofValidation} for that case) — e.g. an invalid enum value, an
 * unparseable identifier. Maps to {@code 400 Bad Request}. See docs/RULES.md §14.
 */
public class BadRequestException extends ClientErrorException {

	private static final int STATUS = 400;
	private static final String CODE = "BAD_REQUEST";

	public BadRequestException(String message) {
		super(message, STATUS, CODE);
	}

	public BadRequestException(String message, Throwable cause) {
		super(message, cause, STATUS, CODE);
	}

}
