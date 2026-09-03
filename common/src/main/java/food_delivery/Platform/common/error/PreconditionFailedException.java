package food_delivery.Platform.common.error;

/**
 * A precondition the caller asserted (an ETag / optimistic-locking version) no longer matches the
 * resource's current state — e.g. two concurrent updates to the same order. Maps to
 * {@code 412 Precondition Failed}. See docs/RULES.md §14.
 */
public class PreconditionFailedException extends ClientErrorException {

	private static final int STATUS = 412;
	private static final String CODE = "PRECONDITION_FAILED";

	public PreconditionFailedException(String message) {
		super(message, STATUS, CODE);
	}

	public PreconditionFailedException(String message, Throwable cause) {
		super(message, cause, STATUS, CODE);
	}

}
