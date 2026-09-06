package food_delivery.Platform.orderservice.client;

import org.springframework.stereotype.Component;

import food_delivery.Platform.common.error.ResourceNotFoundException;
import food_delivery.Platform.common.error.ServiceUnavailableException;
import food_delivery.Platform.orderservice.client.dto.CustomerProfileResponse;
import food_delivery.Platform.orderservice.client.dto.DeliveryAddressResponse;
import feign.FeignException;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;

/**
 * Every outbound call to {@code customer-service} goes through here, never through
 * {@link CustomerServiceClient} directly — this is where RULES.md §7's "circuit breaker, retry,
 * timeout, and bulkhead are all configured explicitly per client" actually lives. "Timeout" is
 * enforced by Feign's own {@code connect-timeout}/{@code read-timeout}
 * (application.properties) rather than Resilience4j's {@code @TimeLimiter}: that annotation
 * requires the guarded method to return {@code CompletableFuture}, which would turn this
 * genuinely synchronous call chain into an async one purely to satisfy the annotation — a real
 * HTTP socket timeout enforced by the client itself accomplishes the same "don't hang forever"
 * guarantee without that complexity.
 *
 * <p>A {@code 404} from {@code customer-service} (no profile, no such address) is translated to
 * {@link ResourceNotFoundException} and deliberately configured as an *ignored* exception for both
 * the circuit breaker and retry instances (application.properties) — it's a legitimate business
 * outcome ("you haven't registered yet"), not evidence the dependency itself is unhealthy, so it
 * must never trip the breaker or trigger a fallback into a misleading {@code 503}.
 */
@Component
public class CustomerServiceGateway {

	private static final String INSTANCE = "customer-service";

	private final CustomerServiceClient client;

	public CustomerServiceGateway(CustomerServiceClient client) {
		this.client = client;
	}

	@CircuitBreaker(name = INSTANCE, fallbackMethod = "profileFallback")
	@Retry(name = INSTANCE)
	@Bulkhead(name = INSTANCE)
	public CustomerProfileResponse getMyProfile() {
		try {
			return client.getMyProfile();
		} catch (FeignException.NotFound e) {
			throw new ResourceNotFoundException(
					"No customer profile exists for this account — POST /api/customers/me on customer-service first.",
					e);
		}
	}

	@SuppressWarnings("unused")
	private CustomerProfileResponse profileFallback(Throwable t) {
		throw new ServiceUnavailableException("customer-service is currently unavailable. Please try again shortly.",
				t);
	}

	@CircuitBreaker(name = INSTANCE, fallbackMethod = "addressFallback")
	@Retry(name = INSTANCE)
	@Bulkhead(name = INSTANCE)
	public DeliveryAddressResponse getMyAddress(Long addressId) {
		try {
			return client.getMyAddress(addressId);
		} catch (FeignException.NotFound e) {
			throw new ResourceNotFoundException("No delivery address " + addressId + " on this account.", e);
		}
	}

	@SuppressWarnings("unused")
	private DeliveryAddressResponse addressFallback(Long addressId, Throwable t) {
		throw new ServiceUnavailableException("customer-service is currently unavailable. Please try again shortly.",
				t);
	}

}
