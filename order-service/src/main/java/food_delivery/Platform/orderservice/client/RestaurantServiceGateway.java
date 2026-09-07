package food_delivery.Platform.orderservice.client;

import java.util.List;

import org.springframework.stereotype.Component;

import food_delivery.Platform.common.error.ResourceNotFoundException;
import food_delivery.Platform.common.error.ServiceUnavailableException;
import food_delivery.Platform.orderservice.client.dto.MenuItemValidationResponse;
import food_delivery.Platform.orderservice.client.dto.RestaurantValidationResponse;
import feign.FeignException;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;

/** See {@link CustomerServiceGateway}'s class comment — identical resilience reasoning applies here. */
@Component
public class RestaurantServiceGateway {

	private static final String INSTANCE = ServiceNames.RESTAURANT_SERVICE;

	private final RestaurantServiceClient client;

	public RestaurantServiceGateway(RestaurantServiceClient client) {
		this.client = client;
	}

	@CircuitBreaker(name = INSTANCE, fallbackMethod = "restaurantFallback")
	@Retry(name = INSTANCE)
	@Bulkhead(name = INSTANCE)
	public RestaurantValidationResponse getRestaurant(Long id) {
		try {
			return client.getRestaurant(id);
		} catch (FeignException.NotFound e) {
			throw new ResourceNotFoundException("No restaurant with id " + id, e);
		}
	}

	@SuppressWarnings("unused")
	private RestaurantValidationResponse restaurantFallback(Long id, Throwable t) {
		throw new ServiceUnavailableException(INSTANCE + " is currently unavailable. Please try again shortly.", t);
	}

	@CircuitBreaker(name = INSTANCE, fallbackMethod = "menuItemsFallback")
	@Retry(name = INSTANCE)
	@Bulkhead(name = INSTANCE)
	public List<MenuItemValidationResponse> getMenuItems(Long restaurantId) {
		try {
			return client.getMenuItems(restaurantId);
		} catch (FeignException.NotFound e) {
			throw new ResourceNotFoundException("No restaurant with id " + restaurantId, e);
		}
	}

	@SuppressWarnings("unused")
	private List<MenuItemValidationResponse> menuItemsFallback(Long restaurantId, Throwable t) {
		throw new ServiceUnavailableException(INSTANCE + " is currently unavailable. Please try again shortly.", t);
	}

}
