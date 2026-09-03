package food_delivery.Platform.common.error;

/**
 * A downstream service (called via Feign, RULES.md §6) returned a response this service couldn't
 * make sense of — malformed, unexpected shape, or a status it doesn't know how to interpret.
 * Distinct from {@link ServiceUnavailableException}: the downstream service responded, it just
 * responded badly. Maps to {@code 502 Bad Gateway}. See docs/RULES.md §14.
 */
public class BadGatewayException extends ServerErrorException {

	private static final int STATUS = 502;
	private static final String CODE = "BAD_GATEWAY";

	public BadGatewayException(String message) {
		super(message, STATUS, CODE);
	}

	public BadGatewayException(String message, Throwable cause) {
		super(message, cause, STATUS, CODE);
	}

}
