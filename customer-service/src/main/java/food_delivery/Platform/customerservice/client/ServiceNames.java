package food_delivery.Platform.customerservice.client;

/**
 * The one place {@code order-service}'s Eureka-registered logical name is spelled out —
 * {@link OrderServiceClient}'s {@code @FeignClient(name = ...)} and {@link OrderServiceGateway}'s
 * resilience4j {@code INSTANCE} constant both reference this instead of repeating the literal, the
 * same single-source-of-truth fix {@code order-service}'s own {@code ServiceNames} class already
 * applied to its three downstream dependencies.
 *
 * <p>{@code application.properties}' own {@code resilience4j.*.instances.order-service.*} and
 * {@code feign.client.config.order-service.*} keys are plain text, not Java — kept in sync with
 * this by hand; that block is commented to say so.
 */
public final class ServiceNames {

	public static final String ORDER_SERVICE = "order-service";

	private ServiceNames() {
	}

}
