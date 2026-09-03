package food_delivery.Platform.common.error;

/**
 * The resource is locked and the request can't proceed until it's released — e.g.
 * {@code identity-service} locking an account after repeated failed logins. Maps to
 * {@code 423 Locked}. See docs/RULES.md §14.
 */
public class LockedException extends ClientErrorException {

	private static final int STATUS = 423;
	private static final String CODE = "LOCKED";

	public LockedException(String message) {
		super(message, STATUS, CODE);
	}

	public LockedException(String message, Throwable cause) {
		super(message, cause, STATUS, CODE);
	}

}
