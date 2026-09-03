package food_delivery.Platform.common.error;

/**
 * The request's {@code Content-Type} isn't one this endpoint can consume. Maps to
 * {@code 415 Unsupported Media Type}. See docs/RULES.md §14.
 */
public class UnsupportedMediaTypeException extends ClientErrorException {

	private static final int STATUS = 415;
	private static final String CODE = "UNSUPPORTED_MEDIA_TYPE";

	public UnsupportedMediaTypeException(String message) {
		super(message, STATUS, CODE);
	}

	public UnsupportedMediaTypeException(String message, Throwable cause) {
		super(message, cause, STATUS, CODE);
	}

}
