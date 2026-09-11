package food_delivery.Platform.customerservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import food_delivery.Platform.customerservice.client.dto.OrderPageResponse;

/**
 * Resolved via Eureka ({@code lb://order-service}), same mechanism as every Feign client
 * elsewhere in this codebase. Calls order-service's own self-service {@code GET /api/orders/me}
 * with the caller's own relayed token ({@link TokenRelayRequestInterceptor}) — order-service
 * already resolves "which customer" from that token itself, so this client never asserts an
 * identity of its own.
 */
@FeignClient(name = ServiceNames.ORDER_SERVICE)
public interface OrderServiceClient {

	@GetMapping("/api/orders/me")
	OrderPageResponse getMyOrders(@RequestParam("size") int size);

}
