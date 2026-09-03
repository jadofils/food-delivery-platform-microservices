package food_delivery.Platform.identityservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PermissionRequest(
		@NotBlank @Size(max = 100)
		@Pattern(regexp = "^[a-z]+(:[a-z]+)*$", message = "must be lower-case colon-separated segments, e.g. order:create")
		String name,
		@Size(max = 255) String description) {
}
