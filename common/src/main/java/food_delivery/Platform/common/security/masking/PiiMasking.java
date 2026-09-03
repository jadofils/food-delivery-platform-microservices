package food_delivery.Platform.common.security.masking;

/**
 * Masks a PII-bearing value for API responses: first character, a fixed-width mask, last
 * character. The mask width is fixed (not proportional to the input) so the mask itself never
 * leaks how long the real value was. See docs/RULES.md §8.
 */
public final class PiiMasking {

	private static final String MASK = "*****";

	/** Below this length there's nothing meaningful left to reveal either side of — mask it whole. */
	private static final int MIN_LENGTH_TO_PARTIALLY_REVEAL = 3;

	private PiiMasking() {
	}

	public static String mask(String value) {
		if (value == null || value.isEmpty()) {
			return value;
		}
		if (value.length() < MIN_LENGTH_TO_PARTIALLY_REVEAL) {
			return MASK;
		}
		return value.charAt(0) + MASK + value.charAt(value.length() - 1);
	}

}
