package food_delivery.Platform.restaurantservice.controller;

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

import food_delivery.Platform.restaurantservice.dto.RestaurantRegistrationRequest;
import food_delivery.Platform.restaurantservice.dto.RestaurantResponse;
import food_delivery.Platform.restaurantservice.dto.RestaurantUpdateRequest;
import food_delivery.Platform.restaurantservice.service.RestaurantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * {@code /me} routes are self-service, gated by {@code restaurant:menu:write} (RULES.md §8) —
 * ownership is resolved from the token's {@code sub} claim, never a client-supplied id. The
 * {@code /{id}}/list routes are public browsing, gated by the weaker {@code restaurant:menu:read}
 * permission the seeded {@code CUSTOMER} demo account also holds (see
 * docker/keycloak/fdp-realm.json) — anyone who can browse restaurants can use them, not just
 * owners/admins.
 */
@RestController
@RequestMapping("/api/restaurants")
@Tag(name = "Restaurants")
public class RestaurantController {

	private final RestaurantService restaurantService;

	public RestaurantController(RestaurantService restaurantService) {
		this.restaurantService = restaurantService;
	}

	@Operation(summary = "Complete registration: create the caller's own restaurant profile")
	@PreAuthorize("hasAuthority('restaurant:menu:write')")
	@PostMapping("/me")
	public ResponseEntity<RestaurantResponse> register(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody RestaurantRegistrationRequest request) {
		RestaurantResponse response = RestaurantResponse.from(restaurantService.register(jwt, request));
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}

	@Operation(summary = "Get the caller's own restaurant profile")
	@PreAuthorize("hasAuthority('restaurant:menu:write')")
	@GetMapping("/me")
	public RestaurantResponse getOwnProfile(@AuthenticationPrincipal Jwt jwt) {
		return RestaurantResponse.from(restaurantService.getOwnProfile(jwt));
	}

	@Operation(summary = "Update the caller's own restaurant profile")
	@PreAuthorize("hasAuthority('restaurant:menu:write')")
	@PutMapping("/me")
	public RestaurantResponse updateOwnProfile(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody RestaurantUpdateRequest request) {
		return RestaurantResponse.from(restaurantService.updateOwnProfile(jwt, request));
	}

	@Operation(summary = "Browse: get any restaurant by id — requires restaurant:menu:read")
	@PreAuthorize("hasAuthority('restaurant:menu:read')")
	@GetMapping("/{id}")
	public RestaurantResponse getById(@PathVariable Long id) {
		return RestaurantResponse.from(restaurantService.getById(id));
	}

	@Operation(summary = "Browse: list all restaurants, paginated — requires restaurant:menu:read")
	@PreAuthorize("hasAuthority('restaurant:menu:read')")
	@GetMapping
	public Page<RestaurantResponse> list(Pageable pageable) {
		return restaurantService.list(pageable).map(RestaurantResponse::from);
	}

}
