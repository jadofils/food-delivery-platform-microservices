package food_delivery.Platform.common.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpMethod;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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
	void handleTypeMismatch_reportsA400NotA500() {
		MethodParameter parameter = mock(MethodParameter.class);
		var ex = new MethodArgumentTypeMismatchException("not-a-number", Long.class, "id", parameter, null);

		HttpServletRequest request = mock(HttpServletRequest.class);
		when(request.getRequestURI()).thenReturn("/api/customers/not-a-number");

		var response = handler.handleTypeMismatch(ex, request);

		assertThat(response.getStatusCode().value()).isEqualTo(400);
		assertThat(response.getBody().error()).isEqualTo("VALIDATION_FAILED");
		assertThat(response.getBody().errors()).hasSize(1);
		assertThat(response.getBody().errors().get(0).field()).isEqualTo("id");
	}

	@Test
	void handleMessageNotReadable_reportsA400NotA500() {
		// Reproduces the real bug: PUT /api/restaurants/me with a JSON body omitting the
		// primitive "isOpen" field was falling through to the 500 catch-all (Jackson can't bind
		// null into a record's primitive component).
		var ex = new HttpMessageNotReadableException("Cannot map `null` into type `boolean`",
				(org.springframework.http.HttpInputMessage) null);

		HttpServletRequest request = mock(HttpServletRequest.class);
		when(request.getRequestURI()).thenReturn("/api/restaurants/me");

		var response = handler.handleMessageNotReadable(ex, request);

		assertThat(response.getStatusCode().value()).isEqualTo(400);
		assertThat(response.getBody().error()).isEqualTo("VALIDATION_FAILED");
	}

	@Test
	void handleNoResourceFound_reportsA404NotA500() {
		// Reproduces the real bug: a copy-pasted Swagger URL with trailing junk
		// ("/swagger-ui/index.html%20%E2%94%82") was falling through to the 500 catch-all.
		var ex = new NoResourceFoundException(HttpMethod.GET, "swagger-ui/index.html%20│", "no such resource");

		HttpServletRequest request = mock(HttpServletRequest.class);
		when(request.getRequestURI()).thenReturn("/swagger-ui/index.html%20%E2%94%82");

		var response = handler.handleNoResourceFound(ex, request);

		assertThat(response.getStatusCode().value()).isEqualTo(404);
		assertThat(response.getBody().error()).isEqualTo("RESOURCE_NOT_FOUND");
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
