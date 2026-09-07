package food_delivery.Platform.restaurantservice.controller;

import java.util.List;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import food_delivery.Platform.restaurantservice.config.CacheConfig;
import food_delivery.Platform.restaurantservice.dto.MenuItemRequest;
import food_delivery.Platform.restaurantservice.dto.MenuItemResponse;
import food_delivery.Platform.restaurantservice.service.MenuItemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@Tag(name = "Menu items")
public class MenuItemController {

	private final MenuItemService menuItemService;

	public MenuItemController(MenuItemService menuItemService) {
		this.menuItemService = menuItemService;
	}

	@Operation(summary = "List the caller's own menu items")
	@PreAuthorize("hasAuthority('restaurant:menu:write')")
	@GetMapping("/api/restaurants/me/menu-items")
	public List<MenuItemResponse> listOwn(@AuthenticationPrincipal Jwt jwt) {
		return menuItemService.listForOwner(jwt).stream().map(MenuItemResponse::from).toList();
	}

	@Operation(summary = "Add a menu item to the caller's own restaurant")
	@PreAuthorize("hasAuthority('restaurant:menu:write')")
	@PostMapping("/api/restaurants/me/menu-items")
	public ResponseEntity<MenuItemResponse> add(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody MenuItemRequest request) {
		MenuItemResponse response = MenuItemResponse.from(menuItemService.addForOwner(jwt, request));
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}

	@Operation(summary = "Get one of the caller's own menu items")
	@PreAuthorize("hasAuthority('restaurant:menu:write')")
	@GetMapping("/api/restaurants/me/menu-items/{menuItemId}")
	public MenuItemResponse getOwn(@AuthenticationPrincipal Jwt jwt, @PathVariable Long menuItemId) {
		return MenuItemResponse.from(menuItemService.getForOwner(jwt, menuItemId));
	}

	@Operation(summary = "Update one of the caller's own menu items")
	@PreAuthorize("hasAuthority('restaurant:menu:write')")
	@PutMapping("/api/restaurants/me/menu-items/{menuItemId}")
	public MenuItemResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable Long menuItemId,
			@Valid @RequestBody MenuItemRequest request) {
		return MenuItemResponse.from(menuItemService.updateForOwner(jwt, menuItemId, request));
	}

	@Operation(summary = "Delete one of the caller's own menu items")
	@PreAuthorize("hasAuthority('restaurant:menu:write')")
	@DeleteMapping("/api/restaurants/me/menu-items/{menuItemId}")
	public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long menuItemId) {
		menuItemService.deleteForOwner(jwt, menuItemId);
		return ResponseEntity.noContent().build();
	}

	/**
	 * Cached in Redis (RULES.md §12) — read-heavy public browsing. Caching the
	 * {@code List<MenuItemResponse>} DTO here, not the {@code List<MenuItem>} entities in the
	 * service layer, for the same reason as {@code RestaurantController.getById}: entities aren't
	 * {@code Serializable} and a lazy {@code @ManyToOne} back-reference is a Hibernate-proxy
	 * serialization risk the DTO doesn't have. {@code MenuItemService}'s
	 * add/update/delete-for-owner methods each evict this same entry on a write.
	 */
	@Operation(summary = "Browse: list a restaurant's menu items — requires restaurant:menu:read")
	@PreAuthorize("hasAuthority('restaurant:menu:read')")
	@GetMapping("/api/restaurants/{restaurantId}/menu-items")
	@Cacheable(cacheNames = CacheConfig.MENU_CACHE, key = "#restaurantId")
	public List<MenuItemResponse> listForRestaurant(@PathVariable Long restaurantId) {
		return menuItemService.listForRestaurant(restaurantId).stream().map(MenuItemResponse::from).toList();
	}

}
