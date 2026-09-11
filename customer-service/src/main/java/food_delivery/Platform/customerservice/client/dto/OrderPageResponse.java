package food_delivery.Platform.customerservice.client.dto;

import java.util.List;

/**
 * order-service's {@code GET /api/orders/me} returns a full Spring Data {@code Page<T>} — page
 * number, size, sort, total counts, and the actual {@code content} array. This record only reads
 * {@code content}; Spring Boot's default (lenient) Jackson configuration ignores every other field
 * in the response rather than failing on them, the same partial-DTO approach every other Feign
 * response type in this codebase already uses (e.g. order-service's own
 * {@code RestaurantValidationResponse}) — no {@code @JsonIgnoreProperties} needed.
 */
public record OrderPageResponse(List<OrderSummaryResponse> content) {
}
