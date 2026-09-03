package food_delivery.Platform.common.error;

/**
 * The request is well-formed but violates a business rule (as opposed to a validation failure —
 * see {@code ApiErrorResponse#ofValidation} for that case). Maps to
 * {@code 422 Unprocessable Entity}. See docs/RULES.md §14.
 */
public class BusinessRuleViolationException extends DomainException {

	private static final String CODE = "BUSINESS_RULE_VIOLATION";
	private static final int STATUS = 422;

	public BusinessRuleViolationException(String message) {
		super(message);
	}

	public BusinessRuleViolationException(String message, Throwable cause) {
		super(message, cause);
	}

	@Override
	public int status() {
		return STATUS;
	}

	@Override
	public String code() {
		return CODE;
	}

}
