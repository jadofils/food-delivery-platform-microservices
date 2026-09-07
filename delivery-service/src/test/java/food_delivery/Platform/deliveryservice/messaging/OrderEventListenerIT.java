package food_delivery.Platform.deliveryservice.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import food_delivery.Platform.common.event.OrderCancelledEvent;
import food_delivery.Platform.common.event.OrderPlacedEvent;
import food_delivery.Platform.deliveryservice.AbstractIntegrationTest;
import food_delivery.Platform.deliveryservice.entity.DeliveryStatus;
import food_delivery.Platform.deliveryservice.repository.DeliveryAssignmentRepository;

/**
 * Publishes real events onto the real exchange/queue topology {@code RabbitConfig} declares — this
 * proves the consumer side end to end, not just the listener method in isolation.
 */
@SpringBootTest
class OrderEventListenerIT extends AbstractIntegrationTest {

	@Autowired
	private RabbitTemplate rabbitTemplate;

	@Autowired
	private DeliveryAssignmentRepository repository;

	@Test
	void orderPlacedEvent_createsAPendingDeliveryAssignment() throws Exception {
		Long orderId = 5001L;
		OrderPlacedEvent event = new OrderPlacedEvent(UUID.randomUUID(), orderId, "kc-delivery-customer-1", 1L, 1L,
				new BigDecimal("11.00"), List.of(new OrderPlacedEvent.Item(1L, "Brochette", new BigDecimal("5.50"), 2)),
				Instant.now());

		rabbitTemplate.convertAndSend("fdp.order-events", "order.placed", event);

		waitUntil(() -> repository.findByOrderId(orderId).isPresent());

		var assignment = repository.findByOrderId(orderId).orElseThrow();
		assertThat(assignment.getStatus()).isEqualTo(DeliveryStatus.PENDING);
		assertThat(assignment.getRestaurantId()).isEqualTo(1L);
		assertThat(assignment.getAssignedAgentKeycloakId()).isNull();
	}

	@Test
	void redeliveredOrderPlacedEvent_isIdempotent_onlyOneAssignmentCreated() throws Exception {
		Long orderId = 5002L;
		OrderPlacedEvent event = new OrderPlacedEvent(UUID.randomUUID(), orderId, "kc-delivery-customer-2", 1L, 1L,
				new BigDecimal("5.50"), List.of(new OrderPlacedEvent.Item(1L, "Brochette", new BigDecimal("5.50"), 1)),
				Instant.now());

		rabbitTemplate.convertAndSend("fdp.order-events", "order.placed", event);
		waitUntil(() -> repository.findByOrderId(orderId).isPresent());

		// Same event, redelivered (simulating RabbitMQ's at-least-once guarantee).
		rabbitTemplate.convertAndSend("fdp.order-events", "order.placed", event);
		Thread.sleep(500);

		assertThat(repository.countByOrderId(orderId)).isEqualTo(1);
	}

	@Test
	void orderCancelledEvent_cancelsThePendingAssignment() throws Exception {
		Long orderId = 5003L;
		OrderPlacedEvent placed = new OrderPlacedEvent(UUID.randomUUID(), orderId, "kc-delivery-customer-3", 1L, 1L,
				new BigDecimal("5.50"), List.of(new OrderPlacedEvent.Item(1L, "Brochette", new BigDecimal("5.50"), 1)),
				Instant.now());
		rabbitTemplate.convertAndSend("fdp.order-events", "order.placed", placed);
		waitUntil(() -> repository.findByOrderId(orderId).isPresent());

		OrderCancelledEvent cancelled = new OrderCancelledEvent(UUID.randomUUID(), orderId, "kc-delivery-customer-3",
				1L, Instant.now());
		rabbitTemplate.convertAndSend("fdp.order-events", "order.cancelled", cancelled);

		waitUntil(() -> repository.findByOrderId(orderId)
				.map(a -> a.getStatus() == DeliveryStatus.CANCELLED)
				.orElse(false));
	}

	private void waitUntil(BooleanSupplier condition) throws InterruptedException {
		for (int i = 0; i < 40; i++) {
			if (condition.getAsBoolean()) {
				return;
			}
			Thread.sleep(250);
		}
		throw new AssertionError("Condition not met within timeout");
	}

}
