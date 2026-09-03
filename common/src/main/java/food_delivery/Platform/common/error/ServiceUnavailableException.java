package food_delivery.Platform.common.error;

/**
 * A downstream dependency is unavailable — the natural type for a Resilience4j fallback method to
 * throw when its circuit breaker is {@code OPEN} (RULES.md §7's example: "menu service
 * unavailable, try again"). Distinct from {@link BadGatewayException}: here the downstream
 * service never responded at all, or is known to be down, rather than responding badly. Maps to
 * {@code 503 Service Unavailable}. See docs/RULES.md §7, §14.
 */
public class ServiceUnavailableException extends ServerErrorException {

	private static final int STATUS = 503;
	private static final String CODE = "SERVICE_UNAVAILABLE";

	public ServiceUnavailableException(String message) {
		super(message, STATUS, CODE);
	}

	public ServiceUnavailableException(String message, Throwable cause) {
		super(message, cause, STATUS, CODE);
	}

}
