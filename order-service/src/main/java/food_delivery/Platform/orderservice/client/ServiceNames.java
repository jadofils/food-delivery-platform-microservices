package food_delivery.Platform.orderservice.client;

/**
 * The one place each downstream service's Eureka-registered logical name is spelled out — every
 * {@code @FeignClient(name = ...)}, every resilience4j {@code INSTANCE} constant, and every
 * fallback's log/error message reference these instead of repeating the literal, so the three
 * gateway/client pairs can never drift out of sync with each other.
 *
 * <p>One thing this can't reach: {@code application.properties}' own
 * {@code resilience4j.*.instances.<name>.*} and {@code feign.client.config.<name>.*} keys are
 * plain text, not Java — a property file can't reference a compiled constant. Those key segments
 * must still be kept in sync with the values here by hand; each block in
 * {@code application.properties} is commented to say so.
 */
public final class ServiceNames {

	public static final String CUSTOMER_SERVICE = "customer-service";
	public static final String RESTAURANT_SERVICE = "restaurant-service";
	public static final String DELIVERY_SERVICE = "delivery-service";

	private ServiceNames() {
	}

}
