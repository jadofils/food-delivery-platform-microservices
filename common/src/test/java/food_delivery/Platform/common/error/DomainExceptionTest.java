package food_delivery.Platform.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.function.Function;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import org.junit.jupiter.api.Test;

class DomainExceptionTest {

	private record Case(Function<String, DomainException> factory, int status, String code,
			Class<? extends DomainException> family) {

		@Override
		public String toString() {
			return status + " " + code;
		}
	}

	private static List<Case> cases() {
		return List.of(
				// 4xx -- ClientErrorException
				new Case(BadRequestException::new, 400, "BAD_REQUEST", ClientErrorException.class),
				new Case(UnauthorizedException::new, 401, "UNAUTHORIZED", ClientErrorException.class),
				new Case(ForbiddenException::new, 403, "FORBIDDEN", ClientErrorException.class),
				new Case(ResourceNotFoundException::new, 404, "RESOURCE_NOT_FOUND", ClientErrorException.class),
				new Case(MethodNotAllowedException::new, 405, "METHOD_NOT_ALLOWED", ClientErrorException.class),
				new Case(NotAcceptableException::new, 406, "NOT_ACCEPTABLE", ClientErrorException.class),
				new Case(RequestTimeoutException::new, 408, "REQUEST_TIMEOUT", ClientErrorException.class),
				new Case(ConflictException::new, 409, "CONFLICT", ClientErrorException.class),
				new Case(GoneException::new, 410, "GONE", ClientErrorException.class),
				new Case(PreconditionFailedException::new, 412, "PRECONDITION_FAILED", ClientErrorException.class),
				new Case(PayloadTooLargeException::new, 413, "PAYLOAD_TOO_LARGE", ClientErrorException.class),
				new Case(UnsupportedMediaTypeException::new, 415, "UNSUPPORTED_MEDIA_TYPE",
						ClientErrorException.class),
				new Case(BusinessRuleViolationException::new, 422, "BUSINESS_RULE_VIOLATION",
						ClientErrorException.class),
				new Case(LockedException::new, 423, "LOCKED", ClientErrorException.class),
				new Case(TooManyRequestsException::new, 429, "TOO_MANY_REQUESTS", ClientErrorException.class),
				// 5xx -- ServerErrorException
				new Case(InternalServerException::new, 500, "INTERNAL_SERVER_ERROR", ServerErrorException.class),
				new Case(BadGatewayException::new, 502, "BAD_GATEWAY", ServerErrorException.class),
				new Case(ServiceUnavailableException::new, 503, "SERVICE_UNAVAILABLE", ServerErrorException.class),
				new Case(GatewayTimeoutException::new, 504, "GATEWAY_TIMEOUT", ServerErrorException.class));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("cases")
	void everySubtype_reportsItsOwnStatusCodeMessageAndFamily(Case testCase) {
		DomainException ex = testCase.factory().apply("boom");

		assertThat(ex.status()).isEqualTo(testCase.status());
		assertThat(ex.code()).isEqualTo(testCase.code());
		assertThat(ex.getMessage()).isEqualTo("boom");
		assertThat(ex).isInstanceOf(testCase.family());
	}

	@Test
	void everyCaseCoversADistinctStatusCode() {
		List<Integer> statuses = cases().stream().map(Case::status).toList();

		assertThat(statuses).doesNotHaveDuplicates();
	}

	@Test
	void everyDomainExceptionSubtype_isUncheckedAndCarriesItsCause() {
		var cause = new IllegalStateException("root cause");
		var ex = new ConflictException("wrapped", cause);

		assertThat(ex).isInstanceOf(RuntimeException.class);
		assertThat(ex.getCause()).isSameAs(cause);
	}

}
