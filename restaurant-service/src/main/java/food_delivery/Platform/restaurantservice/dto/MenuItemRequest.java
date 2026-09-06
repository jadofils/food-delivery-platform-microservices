package food_delivery.Platform.restaurantservice.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MenuItemRequest(

		@NotBlank(message = "name is required") @Size(max = 150) String name,

		@Size(max = 1000) String description,

		@NotNull(message = "price is required")
		@DecimalMin(value = "0.01", message = "price must be greater than 0")
		@Digits(integer = 8, fraction = 2, message = "price must have at most 2 decimal places")
		BigDecimal price,

		@NotBlank(message = "category is required") @Size(max = 50) String category,

		boolean available) {
}
