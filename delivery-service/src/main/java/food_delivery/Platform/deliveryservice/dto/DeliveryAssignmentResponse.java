package food_delivery.Platform.deliveryservice.dto;

import java.time.Instant;

import food_delivery.Platform.deliveryservice.entity.DeliveryAssignment;

public record DeliveryAssignmentResponse(
		Long id,
		Long orderId,
		Long restaurantId,
		Long deliveryAddressId,
		String assignedAgentKeycloakId,
		String status,
		Instant createdAt,
		Instant updatedAt) {

	public static DeliveryAssignmentResponse from(DeliveryAssignment assignment) {
		return new DeliveryAssignmentResponse(
				assignment.getId(),
				assignment.getOrderId(),
				assignment.getRestaurantId(),
				assignment.getDeliveryAddressId(),
				assignment.getAssignedAgentKeycloakId(),
				assignment.getStatus().name(),
				assignment.getCreatedAt(),
				assignment.getUpdatedAt());
	}

}
