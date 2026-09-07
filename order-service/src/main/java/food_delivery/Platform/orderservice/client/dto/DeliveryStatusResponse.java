package food_delivery.Platform.orderservice.client.dto;

/** Only the one field order-service's own order-tracking response actually needs (RULES.md §3). */
public record DeliveryStatusResponse(String status) {
}
