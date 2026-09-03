package food_delivery.Platform.common.error;

/**
 * The request carries no valid credentials at all — no JWT, or one that fails signature/expiry
 * validation. Distinct from {@link ForbiddenException}: this is "we don't know who you are," not
 * "we know who you are and you can't do this." Maps to {@code 401 Unauthorized}. See
 * docs/RULES.md §8, §14.
 */
public class UnauthorizedException extends ClientErrorException {

	private static final int STATUS = 401;
	private static final String CODE = "UNAUTHORIZED";

	public UnauthorizedException(String message) {
		super(message, STATUS, CODE);
	}

	public UnauthorizedException(String message, Throwable cause) {
		super(message, cause, STATUS, CODE);
	}

}
