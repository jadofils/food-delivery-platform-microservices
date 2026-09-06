package food_delivery.Platform.orderservice.messaging;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import food_delivery.Platform.common.event.OrderCancelledEvent;
import food_delivery.Platform.common.event.OrderPlacedEvent;
import food_delivery.Platform.orderservice.entity.Order;
import food_delivery.Platform.orderservice.entity.OrderItem;

/**
 * The one place order-service publishes to RabbitMQ — every event gets its own {@code eventId}
 * (UUID) so a future consumer can dedupe, since RabbitMQ guarantees at-least-once delivery, not
 * exactly-once (RULES.md §6, §1 factor 9).
 */
@Component
public class OrderEventPublisher {

	private final RabbitTemplate rabbitTemplate;

	public OrderEventPublisher(RabbitTemplate rabbitTemplate) {
		this.rabbitTemplate = rabbitTemplate;
	}

	public void publishOrderPlaced(Order order) {
		List<OrderPlacedEvent.Item> items = order.getItems().stream()
				.map(this::toItemPayload)
				.toList();
		OrderPlacedEvent event = new OrderPlacedEvent(
				UUID.randomUUID(),
				order.getId(),
				order.getCustomerKeycloakId(),
				order.getRestaurantId(),
				order.getDeliveryAddressId(),
				order.getTotalAmount(),
				items,
				Instant.now());
		rabbitTemplate.convertAndSend(RabbitConfig.ORDER_EVENTS_EXCHANGE, RabbitConfig.ORDER_PLACED_ROUTING_KEY,
				event);
	}

	public void publishOrderCancelled(Order order) {
		OrderCancelledEvent event = new OrderCancelledEvent(
				UUID.randomUUID(),
				order.getId(),
				order.getCustomerKeycloakId(),
				order.getRestaurantId(),
				Instant.now());
		rabbitTemplate.convertAndSend(RabbitConfig.ORDER_EVENTS_EXCHANGE, RabbitConfig.ORDER_CANCELLED_ROUTING_KEY,
				event);
	}

	private OrderPlacedEvent.Item toItemPayload(OrderItem item) {
		return new OrderPlacedEvent.Item(item.getMenuItemId(), item.getName(), item.getUnitPrice(),
				item.getQuantity());
	}

}
