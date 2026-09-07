package food_delivery.Platform.orderservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import food_delivery.Platform.orderservice.client.dto.CustomerProfileResponse;
import food_delivery.Platform.orderservice.client.dto.DeliveryAddressResponse;

/**
 * The caller (order-service) owns this interface and its response DTOs — not {@code common}
 * (RULES.md §3: a Feign client belongs to the caller, so {@code customer-service} can change its
 * own API without every consumer picking up a shared-module change). Resolved via Eureka
 * ({@code lb://customer-service}, the logical name registered there) — never a hardcoded host
 * (RULES.md §6).
 *
 * <p>Both methods call {@code customer-service}'s own self-service routes with the placing
 * customer's own relayed token ({@link TokenRelayRequestInterceptor}) — there is no separate
 * "service account" or admin credential involved; order-service is acting on behalf of the exact
 * customer who is placing the order, using the same permission they already have.
 */
@FeignClient(name = ServiceNames.CUSTOMER_SERVICE)
public interface CustomerServiceClient {

	@GetMapping("/api/customers/me")
	CustomerProfileResponse getMyProfile();

	@GetMapping("/api/customers/me/addresses/{addressId}")
	DeliveryAddressResponse getMyAddress(@PathVariable("addressId") Long addressId);

}
