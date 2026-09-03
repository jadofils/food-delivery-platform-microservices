package food_delivery.Platform.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DomainExceptionTest {

	@Test
	void resourceNotFoundException_mapsTo404() {
		var ex = new ResourceNotFoundException("customer 42 not found");

		assertThat(ex.status()).isEqualTo(404);
		assertThat(ex.code()).isEqualTo("RESOURCE_NOT_FOUND");
		assertThat(ex.getMessage()).isEqualTo("customer 42 not found");
	}

	@Test
	void businessRuleViolationException_mapsTo422() {
		var ex = new BusinessRuleViolationException("order total must be positive");

		assertThat(ex.status()).isEqualTo(422);
		assertThat(ex.code()).isEqualTo("BUSINESS_RULE_VIOLATION");
	}

	@Test
	void conflictException_mapsTo409() {
		var ex = new ConflictException("email already registered");

		assertThat(ex.status()).isEqualTo(409);
		assertThat(ex.code()).isEqualTo("CONFLICT");
	}

	@Test
	void everyDomainExceptionSubtype_isUncheckedAndCarriesItsCause() {
		var cause = new IllegalStateException("root cause");
		var ex = new ConflictException("wrapped", cause);

		assertThat(ex).isInstanceOf(RuntimeException.class);
		assertThat(ex.getCause()).isSameAs(cause);
	}

}
