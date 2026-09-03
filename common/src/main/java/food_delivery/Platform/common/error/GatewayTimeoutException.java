package food_delivery.Platform.common.error;

/**
 * A downstream call (Feign, RULES.md §6) exceeded its configured timeout before responding —
 * distinct from {@link ServiceUnavailableException}, where the circuit is already open and the
 * call was never attempted. A natural type for a Resilience4j {@code TimeLimiter} fallback to
 * throw. Maps to {@code 504 Gateway Timeout}. See docs/RULES.md §7, §14.
 */
public class GatewayTimeoutException extends ServerErrorException {

	private static final int STATUS = 504;
	private static final String CODE = "GATEWAY_TIMEOUT";

	public GatewayTimeoutException(String message) {
		super(message, STATUS, CODE);
	}

	public GatewayTimeoutException(String message, Throwable cause) {
		super(message, cause, STATUS, CODE);
	}

}
