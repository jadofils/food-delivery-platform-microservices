package food_delivery.Platform.restaurantservice.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import food_delivery.Platform.common.error.ConflictException;
import food_delivery.Platform.common.error.ResourceNotFoundException;
import food_delivery.Platform.common.security.jwt.JwtClaims;
import food_delivery.Platform.restaurantservice.dto.RestaurantRegistrationRequest;
import food_delivery.Platform.restaurantservice.dto.RestaurantUpdateRequest;
import food_delivery.Platform.restaurantservice.entity.Restaurant;
import food_delivery.Platform.restaurantservice.repository.RestaurantRepository;

@Service
public class RestaurantService {

	private final RestaurantRepository restaurantRepository;

	public RestaurantService(RestaurantRepository restaurantRepository) {
		this.restaurantRepository = restaurantRepository;
	}

	/**
	 * Creates this Keycloak owner's FDP restaurant profile (the {@code restaurant:menu:write}
	 * permission, RULES.md §8). One profile per owner identity, so a repeat call is a
	 * {@link ConflictException}, not a silent no-op or a second row.
	 */
	@Transactional
	public Restaurant register(Jwt jwt, RestaurantRegistrationRequest request) {
		String ownerKeycloakId = JwtClaims.subject(jwt);
		if (restaurantRepository.existsByOwnerKeycloakId(ownerKeycloakId)) {
			throw new ConflictException("A restaurant profile already exists for this account.");
		}
		Restaurant restaurant = new Restaurant(
				ownerKeycloakId,
				request.name(),
				request.description(),
				request.cuisineType(),
				request.street(),
				request.city(),
				request.state(),
				request.postalCode(),
				request.country());
		return restaurantRepository.save(restaurant);
	}

	@Transactional(readOnly = true)
	public Restaurant getOwnProfile(Jwt jwt) {
		return findByOwnerOrThrow(JwtClaims.subject(jwt));
	}

	@Transactional
	public Restaurant updateOwnProfile(Jwt jwt, RestaurantUpdateRequest request) {
		Restaurant restaurant = findByOwnerOrThrow(JwtClaims.subject(jwt));
		restaurant.setName(request.name());
		restaurant.setDescription(request.description());
		restaurant.setCuisineType(request.cuisineType());
		restaurant.setStreet(request.street());
		restaurant.setCity(request.city());
		restaurant.setState(request.state());
		restaurant.setPostalCode(request.postalCode());
		restaurant.setCountry(request.country());
		restaurant.setOpen(request.isOpen());
		return restaurant;
	}

	/**
	 * Public browsing (any caller with {@code restaurant:menu:read} — includes plain customers).
	 * Not cached at this layer — see {@code RestaurantController} for why caching lives at the
	 * DTO-conversion boundary instead of around this raw-entity method.
	 */
	@Transactional(readOnly = true)
	public Restaurant getById(Long id) {
		return restaurantRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("No restaurant with id " + id));
	}

	/** Public browsing, paginated. */
	@Transactional(readOnly = true)
	public Page<Restaurant> list(Pageable pageable) {
		return restaurantRepository.findAll(pageable);
	}

	private Restaurant findByOwnerOrThrow(String ownerKeycloakId) {
		return restaurantRepository.findByOwnerKeycloakId(ownerKeycloakId)
				.orElseThrow(() -> new ResourceNotFoundException(
						"No restaurant profile yet for this account — POST /api/restaurants/me first."));
	}

}
