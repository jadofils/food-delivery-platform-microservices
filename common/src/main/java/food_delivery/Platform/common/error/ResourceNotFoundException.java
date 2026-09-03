package food_delivery.Platform.common.error;

/**
 * A requested resource does not exist. Maps to {@code 404 Not Found}. See docs/RULES.md §14.
 */
public class ResourceNotFoundException extends ClientErrorException {

	private static final int STATUS = 404;
	private static final String CODE = "RESOURCE_NOT_FOUND";

	public ResourceNotFoundException(String message) {
		super(message, STATUS, CODE);
	}

	public ResourceNotFoundException(String message, Throwable cause) {
		super(message, cause, STATUS, CODE);
	}

}
