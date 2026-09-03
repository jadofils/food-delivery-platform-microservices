/**
 * Shared error-handling kernel used by every FDP service's global exception handler.
 *
 * <p>Holds the {@link food_delivery.Platform.common.error.DomainException} hierarchy and the
 * {@link food_delivery.Platform.common.error.ApiErrorResponse} envelope described in
 * docs/RULES.md §14. Each service still owns its own {@code @RestControllerAdvice} — this
 * package only supplies the shared types that advice maps from and to, so all nine services
 * return byte-identical error shapes.
 */
package food_delivery.Platform.common.error;
