package food_delivery.Platform.customerservice.client;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import food_delivery.Platform.customerservice.client.dto.OrderSummaryResponse;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;

/**
 * Backs the {@code orders} section of {@code GET /api/customers/me/overview} — a read-side
 * aggregation, not a validation gate, so this deliberately follows {@code order-service}'s own
 * {@code DeliveryServiceGateway} pattern (graceful degradation) rather than
 * {@code CustomerServiceGateway}/{@code RestaurantServiceGateway}'s fail-loud one: whether
 * {@code order-service} is reachable must never make the rest of a customer's own overview
 * (profile, addresses) unavailable (RULES.md §7). Any failure — timeout, circuit open, no
 * instance available — degrades to an empty order list rather than failing the whole overview.
 *
 * <p>Requests a bounded number of most-recent orders ({@link #OVERVIEW_ORDER_LIMIT}), not a fully
 * paginated view — this is a summary overview, not the order-history browsing experience, which
 * already exists as order-service's own {@code GET /api/orders/me}.
 */
@Component
public class OrderServiceGateway {

	private static final Logger log = LoggerFactory.getLogger(OrderServiceGateway.class);
	private static final String INSTANCE = ServiceNames.ORDER_SERVICE;
	private static final int OVERVIEW_ORDER_LIMIT = 20;

	private final OrderServiceClient client;

	public OrderServiceGateway(OrderServiceClient client) {
		this.client = client;
	}

	@CircuitBreaker(name = INSTANCE, fallbackMethod = "myOrdersFallback")
	@Retry(name = INSTANCE)
	@Bulkhead(name = INSTANCE)
	public List<OrderSummaryResponse> getMyOrders() {
		return client.getMyOrders(OVERVIEW_ORDER_LIMIT).content();
	}

	@SuppressWarnings("unused")
	private List<OrderSummaryResponse> myOrdersFallback(Throwable t) {
		log.debug("{} unavailable while building a customer overview: {}", INSTANCE, t.getMessage());
		return List.of();
	}

}
