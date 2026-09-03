package food_delivery.Platform.common.error;

/**
 * A requested resource does not exist. Maps to {@code 404 Not Found}. See docs/RULES.md §14.
 */
public class ResourceNotFoundException extends DomainException {

	private static final String CODE = "RESOURCE_NOT_FOUND";
	private static final int STATUS = 404;

	public ResourceNotFoundException(String message) {
		super(message);
	}

	public ResourceNotFoundException(String message, Throwable cause) {
		super(message, cause);
	}

	@Override
	public int status() {
		return STATUS;
	}

	@Override
	public String code() {
		return CODE;
	}

}
