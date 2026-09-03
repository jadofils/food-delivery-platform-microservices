package food_delivery.Platform.common.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;

class AbstractGlobalExceptionHandlerTest {

	// No abstract methods to implement — this class exists purely so the shared handlers below
	// can't be registered as a Spring bean directly (see class javadoc); a plain anonymous
	// subclass is enough to exercise them here.
	private final AbstractGlobalExceptionHandler handler = new AbstractGlobalExceptionHandler() {
	};

	@Test
	void handleDomainException_usesTheExceptionsOwnStatusAndCode() {
		HttpServletRequest request = mock(HttpServletRequest.class);
		when(request.getRequestURI()).thenReturn("/api/restaurants/7");
		var ex = new ResourceNotFoundException("restaurant 7 not found");

		var response = handler.handleDomainException(ex, request);

		assertThat(response.getStatusCode().value()).isEqualTo(404);
		assertThat(response.getBody().error()).isEqualTo("RESOURCE_NOT_FOUND");
		assertThat(response.getBody().path()).isEqualTo("/api/restaurants/7");
	}

	@Test
	void handleValidation_carriesOneFieldErrorPerInvalidField() {
		BindingResult bindingResult = mock(BindingResult.class);
		when(bindingResult.getFieldErrors())
				.thenReturn(List.of(new org.springframework.validation.FieldError("customer", "email",
						"must not be blank")));
		MethodParameter parameter = mock(MethodParameter.class);
		var ex = new MethodArgumentNotValidException(parameter, bindingResult);

		HttpServletRequest request = mock(HttpServletRequest.class);
		when(request.getRequestURI()).thenReturn("/api/customers");

		var response = handler.handleValidation(ex, request);

		assertThat(response.getStatusCode().value()).isEqualTo(400);
		assertThat(response.getBody().errors()).hasSize(1);
		assertThat(response.getBody().errors().get(0).field()).isEqualTo("email");
	}

	@Test
	void handleConstraintViolation_carriesOneFieldErrorPerViolation() {
		ConstraintViolation<?> violation = mock(ConstraintViolation.class);
		Path path = mock(Path.class);
		when(path.toString()).thenReturn("email");
		when(violation.getPropertyPath()).thenReturn(path);
		when(violation.getMessage()).thenReturn("must not be blank");

		HttpServletRequest request = mock(HttpServletRequest.class);
		when(request.getRequestURI()).thenReturn("/api/customers");

		var ex = new ConstraintViolationException("invalid", Set.of(violation));
		var response = handler.handleConstraintViolation(ex, request);

		assertThat(response.getStatusCode().value()).isEqualTo(400);
		assertThat(response.getBody().errors()).hasSize(1);
		assertThat(response.getBody().errors().get(0).field()).isEqualTo("email");
	}

	@Test
	void handleUnexpected_neverLeaksTheOriginalMessage() {
		HttpServletRequest request = mock(HttpServletRequest.class);
		when(request.getRequestURI()).thenReturn("/api/orders");

		var response = handler.handleUnexpected(new IllegalStateException("db password is hunter2"), request);

		assertThat(response.getStatusCode().value()).isEqualTo(500);
		assertThat(response.getBody().message()).isEqualTo("An unexpected error occurred.");
		assertThat(response.getBody().message()).doesNotContain("hunter2");
	}

}
