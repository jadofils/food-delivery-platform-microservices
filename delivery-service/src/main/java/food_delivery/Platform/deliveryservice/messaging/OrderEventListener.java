package food_delivery.Platform.deliveryservice.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import food_delivery.Platform.common.event.OrderCancelledEvent;
import food_delivery.Platform.common.event.OrderPlacedEvent;
import food_delivery.Platform.deliveryservice.entity.DeliveryAssignment;
import food_delivery.Platform.deliveryservice.entity.DeliveryStatus;
import food_delivery.Platform.deliveryservice.repository.DeliveryAssignmentRepository;

/**
 * Consumes {@code order-service}'s published events off {@code delivery-service.order-events}
 * (RULES.md §6): {@code OrderPlacedEvent} auto-creates a delivery assignment (this service's own
 * exit criterion, SPRINTS.md Sprint 5 — "placing an order produces a delivery record automatically
 * with no synchronous call from order-service"); {@code OrderCancelledEvent} cancels the assignment
 * if the delivery hasn't already completed.
 *
 * <p>Takes the raw {@link Message} and converts it explicitly via the injected
 * {@link MessageConverter}, the same fix {@code notification-service}'s equivalent listener needed
 * first — a generic {@code Object}-typed {@code @RabbitListener} parameter doesn't give Spring's
 * listener adapter enough type information to convert automatically, and silently hands the method
 * the untouched {@code Message} instead.
 *
 * <p>Idempotent by construction, but not by the {@code existsByOrderId} check alone — that's a
 * plain find-then-save with no locking, so it is NOT a correctness guarantee under a genuinely
 * concurrent redelivery (the exact TOCTOU gap {@code notification-service}'s own listener had to
 * learn the hard way, see its javadoc). The real guard is {@code DeliveryAssignment.orderId}'s
 * unique index: a losing concurrent insert is rejected by Postgres itself with
 * {@link DataIntegrityViolationException}, caught below and treated as "already processed."
 */
@Component
public class OrderEventListener {

	private static final Logger log = LoggerFactory.getLogger(OrderEventListener.class);

	private final DeliveryAssignmentRepository repository;
	private final MessageConverter messageConverter;

	public OrderEventListener(DeliveryAssignmentRepository repository, MessageConverter messageConverter) {
		this.repository = repository;
		this.messageConverter = messageConverter;
	}

	@RabbitListener(queues = "delivery-service.order-events")
	public void handle(Message message) {
		Object event = messageConverter.fromMessage(message);
		if (event instanceof OrderPlacedEvent placed) {
			handleOrderPlaced(placed);
		} else if (event instanceof OrderCancelledEvent cancelled) {
			handleOrderCancelled(cancelled);
		} else {
			log.warn("Unrecognized event type on delivery-service.order-events: {}",
					event == null ? "null" : event.getClass());
		}
	}

	private void handleOrderPlaced(OrderPlacedEvent event) {
		if (repository.existsByOrderId(event.orderId())) {
			log.debug("Duplicate delivery of OrderPlacedEvent for order {} - already assigned, skipping.",
					event.orderId());
			return;
		}
		DeliveryAssignment assignment = new DeliveryAssignment(event.orderId(), event.restaurantId(),
				event.customerKeycloakId(), event.deliveryAddressId());
		try {
			repository.save(assignment);
		} catch (DataIntegrityViolationException e) {
			log.debug("Duplicate delivery of OrderPlacedEvent for order {} - unique index rejected the write, "
					+ "skipping.", event.orderId());
		}
	}

	private void handleOrderCancelled(OrderCancelledEvent event) {
		repository.findByOrderId(event.orderId()).ifPresent(assignment -> {
			if (assignment.getStatus() == DeliveryStatus.DELIVERED || assignment.getStatus() == DeliveryStatus.CANCELLED) {
				log.debug("Order {} already {}, ignoring cancellation.", event.orderId(), assignment.getStatus());
				return;
			}
			assignment.setStatus(DeliveryStatus.CANCELLED);
			repository.save(assignment);
		});
	}

}
