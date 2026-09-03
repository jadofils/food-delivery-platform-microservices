package food_delivery.Platform.common.error;

/**
 * The caller is authenticated but lacks the permission this endpoint requires — the outcome of a
 * failed {@code @PreAuthorize} check against the JWT's permission claims (RULES.md §8). Distinct
 * from {@link UnauthorizedException}: this is "we know who you are and you still can't do this."
 * Maps to {@code 403 Forbidden}. See docs/RULES.md §8, §14.
 */
public class ForbiddenException extends ClientErrorException {

	private static final int STATUS = 403;
	private static final String CODE = "FORBIDDEN";

	public ForbiddenException(String message) {
		super(message, STATUS, CODE);
	}

	public ForbiddenException(String message, Throwable cause) {
		super(message, cause, STATUS, CODE);
	}

}
