package food_delivery.Platform.orderservice.client.dto;

/** order-service's own minimal view of {@code restaurant-service}'s restaurant — see {@link CustomerProfileResponse}. */
public record RestaurantValidationResponse(Long id, String name, boolean isOpen) {
}
