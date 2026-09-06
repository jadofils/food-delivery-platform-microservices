package food_delivery.Platform.orderservice.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record OrderItemRequest(

		@NotNull(message = "menuItemId is required") Long menuItemId,

		@Min(value = 1, message = "quantity must be at least 1") int quantity) {
}
