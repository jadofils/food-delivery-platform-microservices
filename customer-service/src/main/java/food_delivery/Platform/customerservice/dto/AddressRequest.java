package food_delivery.Platform.customerservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddressRequest(

		@NotBlank(message = "label is required") @Size(max = 50) String label,

		@NotBlank(message = "street is required") @Size(max = 200) String street,

		@NotBlank(message = "city is required") @Size(max = 100) String city,

		@NotBlank(message = "state is required") @Size(max = 100) String state,

		@NotBlank(message = "postalCode is required") @Size(max = 20) String postalCode,

		@NotBlank(message = "country is required") @Size(max = 100) String country,

		boolean isDefault) {
}
