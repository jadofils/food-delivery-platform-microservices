package food_delivery.Platform.common.error;

/**
 * A recognized failure mode this service can't recover from — as opposed to the truly unplanned
 * exceptions {@code AbstractGlobalExceptionHandler}'s catch-all handles. Throw this deliberately
 * when code detects a broken invariant it has no fallback for; let everything else fall through
 * to the catch-all. Client-visible output is identical either way (RULES.md §14 never leaks
 * details) — the distinction is for server-side logs and metrics, so "we saw this coming" and
 * "we didn't" are distinguishable by {@code error} code without telling the client anything more.
 * Maps to {@code 500 Internal Server Error}. See docs/RULES.md §14.
 */
public class InternalServerException extends ServerErrorException {

	private static final int STATUS = 500;
	private static final String CODE = "INTERNAL_SERVER_ERROR";

	public InternalServerException(String message) {
		super(message, STATUS, CODE);
	}

	public InternalServerException(String message, Throwable cause) {
		super(message, cause, STATUS, CODE);
	}

}
