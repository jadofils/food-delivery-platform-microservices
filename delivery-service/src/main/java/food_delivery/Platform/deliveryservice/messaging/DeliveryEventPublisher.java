package food_delivery.Platform.deliveryservice.messaging;

import java.time.Instant;
import java.util.UUID;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import food_delivery.Platform.common.event.DeliveryStatusUpdatedEvent;
import food_delivery.Platform.deliveryservice.entity.DeliveryAssignment;

/**
 * Publishes {@code DeliveryStatusUpdatedEvent} on every status transition (claim, pickup, deliver)
 * — {@code notification-service} consumes this to record that the customer was notified
 * (docs/services/delivery-service.md). One event per transition, not just pickup/deliver: a
 * customer being told "a driver has been assigned" is as much a real notification-worthy action as
 * the later ones.
 */
@Component
public class DeliveryEventPublisher {

	private final RabbitTemplate rabbitTemplate;

	public DeliveryEventPublisher(RabbitTemplate rabbitTemplate) {
		this.rabbitTemplate = rabbitTemplate;
	}

	public void publishStatusUpdated(DeliveryAssignment assignment) {
		DeliveryStatusUpdatedEvent event = new DeliveryStatusUpdatedEvent(
				UUID.randomUUID(),
				assignment.getOrderId(),
				assignment.getId(),
				assignment.getCustomerKeycloakId(),
				assignment.getStatus().name(),
				assignment.getAssignedAgentKeycloakId(),
				Instant.now());
		rabbitTemplate.convertAndSend(RabbitConfig.DELIVERY_EVENTS_EXCHANGE,
				RabbitConfig.DELIVERY_STATUS_UPDATED_ROUTING_KEY, event);
	}

}
