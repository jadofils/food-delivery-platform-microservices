package food_delivery.Platform.identityservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RoleRequest(
		@NotBlank @Size(max = 50)
		@Pattern(regexp = "^[A-Z][A-Z_]*$", message = "must be upper-case with underscores, e.g. RESTAURANT_OWNER")
		String name,
		@Size(max = 255) String description) {
}
