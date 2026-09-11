package food_delivery.Platform.customerservice.controller;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import food_delivery.Platform.customerservice.client.OrderServiceGateway;
import food_delivery.Platform.customerservice.dto.AddressResponse;
import food_delivery.Platform.customerservice.dto.CustomerOverviewResponse;
import food_delivery.Platform.customerservice.dto.CustomerRegistrationRequest;
import food_delivery.Platform.customerservice.dto.CustomerResponse;
import food_delivery.Platform.customerservice.dto.CustomerUpdateRequest;
import food_delivery.Platform.customerservice.service.AddressService;
import food_delivery.Platform.customerservice.service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * {@code /me} routes are self-service: any authenticated caller may act on their own profile, no
 * permission beyond a valid token required — ownership is resolved from the token's {@code sub}
 * claim, never from a client-supplied id. {@code GET /me/overview} is the same self-service model
 * extended into a "customer 360" view: this service's own profile/addresses plus a live, best-effort
 * summary of the caller's order history pulled from {@code order-service} (RULES.md §6) — see
 * {@link food_delivery.Platform.customerservice.client.OrderServiceGateway}'s own class comment
 * for why that half degrades gracefully instead of failing the whole response. The two
 * {@code /{id}}/list routes are the admin-facing counterpart, gated by the {@code user:read}
 * permission (RULES.md §8) — the seeded {@code CUSTOMER} demo account does NOT have it, only
 * {@code ADMIN} does (see docker/keycloak/fdp-realm.json), so the two halves of this controller are
 * exercised by two different demo accounts in Postman.
 */
@RestController
@RequestMapping("/api/customers")
@Tag(name = "Customers")
public class CustomerController {

	private final CustomerService customerService;
	private final AddressService addressService;
	private final OrderServiceGateway orderServiceGateway;

	public CustomerController(CustomerService customerService, AddressService addressService,
			OrderServiceGateway orderServiceGateway) {
		this.customerService = customerService;
		this.addressService = addressService;
		this.orderServiceGateway = orderServiceGateway;
	}

	@Operation(summary = "Complete registration: create the caller's FDP customer profile")
	@PostMapping("/me")
	public ResponseEntity<CustomerResponse> register(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody CustomerRegistrationRequest request) {
		CustomerResponse response = CustomerResponse.from(customerService.register(jwt, request));
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}

	@Operation(summary = "Get the caller's own profile")
	@GetMapping("/me")
	public CustomerResponse getOwnProfile(@AuthenticationPrincipal Jwt jwt) {
		return CustomerResponse.from(customerService.getOwnProfile(jwt));
	}

	@Operation(summary = "Get the caller's own profile, addresses, and recent order history (with live delivery status) in one call")
	@GetMapping("/me/overview")
	public CustomerOverviewResponse getOwnOverview(@AuthenticationPrincipal Jwt jwt) {
		CustomerResponse profile = CustomerResponse.from(customerService.getOwnProfile(jwt));
		List<AddressResponse> addresses = addressService.listForOwner(jwt).stream().map(AddressResponse::from)
				.toList();
		return new CustomerOverviewResponse(profile, addresses, orderServiceGateway.getMyOrders());
	}

	@Operation(summary = "Update the caller's own profile")
	@PutMapping("/me")
	public CustomerResponse updateOwnProfile(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody CustomerUpdateRequest request) {
		return CustomerResponse.from(customerService.updateOwnProfile(jwt, request));
	}

	@Operation(summary = "Admin: get any customer by id — requires user:read")
	@PreAuthorize("hasAuthority('user:read')")
	@GetMapping("/{id}")
	public CustomerResponse getById(@PathVariable Long id) {
		return CustomerResponse.from(customerService.getById(id));
	}

	@Operation(summary = "Admin: list all customers, paginated — requires user:read")
	@PreAuthorize("hasAuthority('user:read')")
	@GetMapping
	public Page<CustomerResponse> list(Pageable pageable) {
		return customerService.list(pageable).map(CustomerResponse::from);
	}

}
