package food_delivery.Platform.common.error;

/**
 * The resource exists but doesn't support the HTTP method used. Spring MVC already raises
 * {@code HttpRequestMethodNotSupportedException} for this automatically at the framework level;
 * this type exists for the rarer case where application code recognizes the same condition
 * itself. Maps to {@code 405 Method Not Allowed}. See docs/RULES.md §14.
 */
public class MethodNotAllowedException extends ClientErrorException {

	private static final int STATUS = 405;
	private static final String CODE = "METHOD_NOT_ALLOWED";

	public MethodNotAllowedException(String message) {
		super(message, STATUS, CODE);
	}

	public MethodNotAllowedException(String message, Throwable cause) {
		super(message, cause, STATUS, CODE);
	}

}
