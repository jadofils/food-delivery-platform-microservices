package food_delivery.Platform.customerservice.dto;

import java.util.List;

import food_delivery.Platform.customerservice.client.dto.OrderSummaryResponse;

/**
 * Self-service "customer 360" view (RULES.md §6) — the caller's own profile and addresses (owned
 * by this service) plus their recent order history with live delivery status per order (fetched
 * from {@code order-service} via {@link food_delivery.Platform.customerservice.client.OrderServiceGateway}).
 * Deliberately stops there: no notification history, no restaurant/menu details for ordered items
 * — a bigger aggregation was considered and explicitly scoped down to this.
 *
 * <p>{@code orders} is never {@code null} — empty (not absent) when {@code order-service} is
 * unreachable or the customer simply has no orders yet, so a client never has to null-check it.
 */
public record CustomerOverviewResponse(
		CustomerResponse profile,
		List<AddressResponse> addresses,
		List<OrderSummaryResponse> orders) {
}
