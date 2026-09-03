package food_delivery.Platform.common.error;

/**
 * Base type for every unchecked, business-rule-carrying exception raised from a service's
 * business/service layer. Unchecked by design: Spring's exception translation,
 * {@code @Transactional}'s rollback-on-unchecked default, and {@code @RestControllerAdvice} are
 * all built around unchecked propagation. Checked exceptions are reserved narrowly for cases
 * where forcing the caller to acknowledge failure at compile time earns its keep (e.g. a Feign
 * fallback method's declared failure mode) — see docs/RULES.md §14.
 *
 * <p>Only HTTP 4xx and 5xx are represented here — see {@link ClientErrorException} and
 * {@link ServerErrorException}. 1xx (informational) and 2xx (success) are never thrown; they're
 * a controller method's normal return value. 3xx (redirection) has no meaningful place in a JSON
 * REST API. An exception hierarchy exists to represent things that went wrong, not things that
 * went right or a protocol-level handshake.
 *
 * <p>{@code status} and {@code code} are constructor arguments stored once here, rather than a
 * method every leaf class overrides — with almost twenty leaf types, that would be nineteen
 * near-identical method pairs instead of nineteen two-line constructors.
 */
public abstract class DomainException extends RuntimeException implements ApiError {

	private final int status;
	private final String code;

	protected DomainException(String message, int status, String code) {
		super(message);
		this.status = status;
		this.code = code;
	}

	protected DomainException(String message, Throwable cause, int status, String code) {
		super(message, cause);
		this.status = status;
		this.code = code;
	}

	@Override
	public final int status() {
		return status;
	}

	@Override
	public final String code() {
		return code;
	}

}
