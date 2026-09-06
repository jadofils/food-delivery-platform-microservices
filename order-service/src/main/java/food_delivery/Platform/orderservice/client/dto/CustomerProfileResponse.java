package food_delivery.Platform.orderservice.client.dto;

/**
 * order-service's own minimal view of {@code customer-service}'s {@code GET /api/customers/me}
 * response — only the fields this service actually uses (RULES.md §3: "the caller should declare
 * only the fields it actually uses; an unrelated field added by the producer shouldn't force a
 * rebuild"). Unknown JSON properties (customer-service's masked {@code email}/{@code phoneNumber},
 * {@code createdAt}, …) are silently ignored by Jackson's default lenient deserialization — this
 * record never needs to change just because {@code customer-service} adds a field.
 */
public record CustomerProfileResponse(Long id, String firstName, String lastName) {
}
