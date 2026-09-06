package food_delivery.Platform.restaurantservice.dto;

import java.math.BigDecimal;

import food_delivery.Platform.restaurantservice.entity.MenuItem;

public record MenuItemResponse(
		Long id,
		String name,
		String description,
		BigDecimal price,
		String category,
		boolean available) {

	public static MenuItemResponse from(MenuItem menuItem) {
		return new MenuItemResponse(
				menuItem.getId(),
				menuItem.getName(),
				menuItem.getDescription(),
				menuItem.getPrice(),
				menuItem.getCategory(),
				menuItem.isAvailable());
	}

}
