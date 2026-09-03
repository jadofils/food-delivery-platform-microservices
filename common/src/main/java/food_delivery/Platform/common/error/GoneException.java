package food_delivery.Platform.common.error;

/**
 * The resource used to exist but was deliberately, permanently removed — distinct from
 * {@link ResourceNotFoundException}, which makes no claim about whether the resource ever
 * existed. Maps to {@code 410 Gone}. See docs/RULES.md §14.
 */
public class GoneException extends ClientErrorException {

	private static final int STATUS = 410;
	private static final String CODE = "GONE";

	public GoneException(String message) {
		super(message, STATUS, CODE);
	}

	public GoneException(String message, Throwable cause) {
		super(message, cause, STATUS, CODE);
	}

}
