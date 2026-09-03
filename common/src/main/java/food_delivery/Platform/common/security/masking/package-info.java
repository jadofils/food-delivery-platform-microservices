/**
 * PII masking for API responses. Annotate a DTO field/record component with
 * {@link food_delivery.Platform.common.security.masking.Masked} to have it serialize as
 * {@code first-char + fixed-width-mask + last-char} instead of its real value. See
 * docs/RULES.md §8 for what should and shouldn't be masked.
 */
package food_delivery.Platform.common.security.masking;
