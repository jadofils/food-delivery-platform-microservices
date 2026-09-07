package food_delivery.Platform.orderservice.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import feign.FeignException;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;

/**
 * Deliberately diverges from {@link CustomerServiceGateway}/{@link RestaurantServiceGateway}'s
 * fail-loud pattern: those guard order *placement* — a missing customer profile or restaurant is a
 * real reason to reject the request. This guards a *read-side enrichment* of an already-placed
 * order's own detail view: whether {@code delivery-service} has an assignment yet, or is reachable
 * at all, must never make the order itself unviewable (RULES.md §7 — the system keeps functioning
 * when a dependency is down). Both "no assignment yet" (404 — e.g. the async consumer hasn't caught
 * up with a just-placed order) and "delivery-service is unavailable" degrade to a {@code null}
 * delivery status rather than failing the whole {@code GET /api/orders/me/{id}} request.
 */
@Component
public class DeliveryServiceGateway {

	private static final Logger log = LoggerFactory.getLogger(DeliveryServiceGateway.class);
	private static final String INSTANCE = "delivery-service";

	private final DeliveryServiceClient client;

	public DeliveryServiceGateway(DeliveryServiceClient client) {
		this.client = client;
	}

	@CircuitBreaker(name = INSTANCE, fallbackMethod = "statusFallback")
	@Retry(name = INSTANCE)
	@Bulkhead(name = INSTANCE)
	public String getStatusByOrderId(Long orderId) {
		try {
			return client.getByOrderId(orderId).status();
		} catch (FeignException.NotFound e) {
			log.debug("No delivery assignment yet for order {} — likely just placed.", orderId);
			return null;
		}
	}

	@SuppressWarnings("unused")
	private String statusFallback(Long orderId, Throwable t) {
		log.debug("delivery-service unavailable while enriching order {} with delivery status: {}", orderId,
				t.getMessage());
		return null;
	}

}
