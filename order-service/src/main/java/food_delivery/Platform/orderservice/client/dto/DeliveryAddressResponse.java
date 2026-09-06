package food_delivery.Platform.orderservice.client.dto;

/** order-service's own minimal view of one of {@code customer-service}'s addresses — see {@link CustomerProfileResponse}. */
public record DeliveryAddressResponse(Long id, String street, String city, String state, String postalCode,
		String country) {
}
