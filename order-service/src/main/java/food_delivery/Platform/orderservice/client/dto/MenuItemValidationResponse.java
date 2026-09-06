package food_delivery.Platform.orderservice.client.dto;

import java.math.BigDecimal;

/** order-service's own minimal view of one of {@code restaurant-service}'s menu items — see {@link CustomerProfileResponse}. */
public record MenuItemValidationResponse(Long id, String name, BigDecimal price, boolean available) {
}
