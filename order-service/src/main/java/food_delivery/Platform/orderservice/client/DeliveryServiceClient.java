package food_delivery.Platform.orderservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import food_delivery.Platform.orderservice.client.dto.DeliveryStatusResponse;

/**
 * Resolved via Eureka ({@code lb://delivery-service}), same as {@link CustomerServiceClient}/
 * {@link RestaurantServiceClient}. Calls {@code delivery-service}'s self-service
 * {@code /by-order/{orderId}} route with the placing customer's own relayed token
 * ({@link TokenRelayRequestInterceptor}) — {@code delivery-service} checks ownership itself
 * (RULES.md §8); order-service never asserts an identity it doesn't already have.
 */
@FeignClient(name = ServiceNames.DELIVERY_SERVICE)
public interface DeliveryServiceClient {

	@GetMapping("/api/deliveries/by-order/{orderId}")
	DeliveryStatusResponse getByOrderId(@PathVariable("orderId") Long orderId);

}
