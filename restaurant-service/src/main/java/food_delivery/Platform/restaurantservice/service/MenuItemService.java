package food_delivery.Platform.restaurantservice.service;

import java.util.List;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import food_delivery.Platform.common.error.ResourceNotFoundException;
import food_delivery.Platform.common.security.jwt.JwtClaims;
import food_delivery.Platform.restaurantservice.config.CacheConfig;
import food_delivery.Platform.restaurantservice.dto.MenuItemRequest;
import food_delivery.Platform.restaurantservice.entity.MenuItem;
import food_delivery.Platform.restaurantservice.entity.Restaurant;
import food_delivery.Platform.restaurantservice.repository.MenuItemRepository;
import food_delivery.Platform.restaurantservice.repository.RestaurantRepository;

/**
 * Self-service methods here resolve "which restaurant" from the caller's own JWT, never from a
 * client-supplied restaurant id — an owner can only ever manage their own menu (RULES.md §8).
 * {@link #listForRestaurant} is the one deliberately different method: public browsing by
 * restaurant id, for any caller holding {@code restaurant:menu:read} (includes plain customers) —
 * see {@code MenuItemController}.
 */
@Service
public class MenuItemService {

	private final MenuItemRepository menuItemRepository;
	private final RestaurantRepository restaurantRepository;

	public MenuItemService(MenuItemRepository menuItemRepository, RestaurantRepository restaurantRepository) {
		this.menuItemRepository = menuItemRepository;
		this.restaurantRepository = restaurantRepository;
	}

	@Transactional(readOnly = true)
	public List<MenuItem> listForOwner(Jwt jwt) {
		Restaurant restaurant = ownerOf(jwt);
		return restaurantRepository.findWithMenuItemsById(restaurant.getId())
				.map(Restaurant::getMenuItems)
				.orElseGet(List::of);
	}

	/**
	 * Evicts the restaurant's cached menu listing (RULES.md §12) — {@code #result.restaurant.id}
	 * reads the id off {@code MenuItem.restaurant}, a LAZY {@code @ManyToOne}; Hibernate resolves an
	 * association's own id from the owning foreign key without needing to actually fetch the parent
	 * row, so this doesn't force an extra query.
	 */
	@Transactional
	@CacheEvict(cacheNames = CacheConfig.MENU_CACHE, key = "#result.restaurant.id")
	public MenuItem addForOwner(Jwt jwt, MenuItemRequest request) {
		Restaurant restaurant = ownerOf(jwt);
		MenuItem menuItem = new MenuItem(request.name(), request.description(), request.price(), request.category(),
				request.available());
		restaurant.addMenuItem(menuItem);
		return menuItemRepository.save(menuItem);
	}

	@Transactional(readOnly = true)
	public MenuItem getForOwner(Jwt jwt, Long menuItemId) {
		Restaurant restaurant = ownerOf(jwt);
		return menuItemRepository.findByIdAndRestaurantId(menuItemId, restaurant.getId())
				.orElseThrow(() -> new ResourceNotFoundException("No menu item " + menuItemId + " on this restaurant."));
	}

	@Transactional
	@CacheEvict(cacheNames = CacheConfig.MENU_CACHE, key = "#result.restaurant.id")
	public MenuItem updateForOwner(Jwt jwt, Long menuItemId, MenuItemRequest request) {
		MenuItem menuItem = getForOwner(jwt, menuItemId);
		menuItem.setName(request.name());
		menuItem.setDescription(request.description());
		menuItem.setPrice(request.price());
		menuItem.setCategory(request.category());
		menuItem.setAvailable(request.available());
		return menuItem;
	}

	/**
	 * Returns the now-former owning restaurant's id (rather than {@code void}) purely so
	 * {@code @CacheEvict} has something to key on — {@code #result} isn't available for a
	 * {@code void} method. The controller's {@code 204 No Content} response is unaffected; it simply
	 * doesn't use the return value.
	 */
	@Transactional
	@CacheEvict(cacheNames = CacheConfig.MENU_CACHE, key = "#result")
	public Long deleteForOwner(Jwt jwt, Long menuItemId) {
		MenuItem menuItem = getForOwner(jwt, menuItemId);
		Restaurant restaurant = menuItem.getRestaurant();
		restaurant.removeMenuItem(menuItem);
		return restaurant.getId();
	}

	/** Public browsing — no ownership/JWT involved beyond the route's {@code restaurant:menu:read} gate. */
	@Transactional(readOnly = true)
	public List<MenuItem> listForRestaurant(Long restaurantId) {
		if (!restaurantRepository.existsById(restaurantId)) {
			throw new ResourceNotFoundException("No restaurant with id " + restaurantId);
		}
		return menuItemRepository.findByRestaurantId(restaurantId);
	}

	private Restaurant ownerOf(Jwt jwt) {
		String ownerKeycloakId = JwtClaims.subject(jwt);
		return restaurantRepository.findByOwnerKeycloakId(ownerKeycloakId)
				.orElseThrow(() -> new ResourceNotFoundException(
						"No restaurant profile yet for this account — POST /api/restaurants/me first."));
	}

}
