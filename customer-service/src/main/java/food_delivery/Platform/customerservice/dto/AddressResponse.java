package food_delivery.Platform.customerservice.dto;

import food_delivery.Platform.customerservice.entity.Address;

/**
 * Not PII in the sense RULES.md §8 means by "human-readable PII" (email, username, phone) — a
 * delivery address is operational data the recipient (a restaurant, a delivery agent) legitimately
 * needs to see unmasked to do their job, so nothing here is {@code @Masked}.
 */
public record AddressResponse(
		Long id,
		String label,
		String street,
		String city,
		String state,
		String postalCode,
		String country,
		boolean isDefault) {

	public static AddressResponse from(Address address) {
		return new AddressResponse(
				address.getId(),
				address.getLabel(),
				address.getStreet(),
				address.getCity(),
				address.getState(),
				address.getPostalCode(),
				address.getCountry(),
				address.isDefault());
	}

}
