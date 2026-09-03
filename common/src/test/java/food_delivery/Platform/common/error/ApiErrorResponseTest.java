package food_delivery.Platform.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import food_delivery.Platform.common.error.ApiErrorResponse.FieldError;

class ApiErrorResponseTest {

	@Test
	void of_buildsEnvelopeFromADomainException() {
		var ex = new ResourceNotFoundException("restaurant 7 not found");

		var response = ApiErrorResponse.of(ex, ex.getMessage(), "/api/restaurants/7", "trace-123");

		assertThat(response.status()).isEqualTo(404);
		assertThat(response.error()).isEqualTo("RESOURCE_NOT_FOUND");
		assertThat(response.message()).isEqualTo("restaurant 7 not found");
		assertThat(response.path()).isEqualTo("/api/restaurants/7");
		assertThat(response.traceId()).isEqualTo("trace-123");
		assertThat(response.errors()).isEmpty();
		assertThat(response.timestamp()).isNotNull();
	}

	@Test
	void ofValidation_carriesOneFieldErrorPerInvalidField() {
		var fieldErrors = List.of(new FieldError("email", "must not be blank"),
				new FieldError("phone", "must be a valid phone number"));

		var response = ApiErrorResponse.ofValidation("validation failed", "/api/customers", "trace-456",
				fieldErrors);

		assertThat(response.status()).isEqualTo(400);
		assertThat(response.error()).isEqualTo("VALIDATION_FAILED");
		assertThat(response.errors()).hasSize(2);
	}

	@Test
	void ofUnexpected_neverLeaksTheUnderlyingMessage() {
		var response = ApiErrorResponse.ofUnexpected("/api/orders", "trace-789");

		assertThat(response.status()).isEqualTo(500);
		assertThat(response.error()).isEqualTo("INTERNAL_ERROR");
		assertThat(response.message()).isEqualTo("An unexpected error occurred.");
	}

}
