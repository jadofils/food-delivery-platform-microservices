package food_delivery.Platform.common.error;

/**
 * The request conflicts with the resource's current state (e.g. a duplicate registration). Maps
 * to {@code 409 Conflict}. See docs/RULES.md §14.
 */
public class ConflictException extends DomainException {

	private static final String CODE = "CONFLICT";
	private static final int STATUS = 409;

	public ConflictException(String message) {
		super(message);
	}

	public ConflictException(String message, Throwable cause) {
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
