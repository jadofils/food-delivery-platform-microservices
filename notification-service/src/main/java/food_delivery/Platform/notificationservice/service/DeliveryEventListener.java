package food_delivery.Platform.notificationservice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import food_delivery.Platform.common.event.DeliveryStatusUpdatedEvent;
import food_delivery.Platform.notificationservice.entity.NotificationRecord;
import food_delivery.Platform.notificationservice.repository.NotificationRecordRepository;

/**
 * Consumes {@code delivery-service}'s published events off
 * {@code notification-service.delivery-events} (RULES.md §6) — the pair to {@link
 * OrderEventListener}, same reasoning throughout: raw {@link Message} + explicit conversion (a
 * generic {@code Object}-typed listener parameter doesn't get automatic conversion), and
 * {@code NotificationRecord.eventId}'s unique index as the real idempotency guard, with the
 * {@code findByEventId} check only a fast-path optimization on top of it — see
 * {@link OrderEventListener}'s own javadoc for the full story of both lessons.
 */
@Component
public class DeliveryEventListener {

	private static final Logger log = LoggerFactory.getLogger(DeliveryEventListener.class);

	private final NotificationRecordRepository repository;
	private final MessageConverter messageConverter;

	public DeliveryEventListener(NotificationRecordRepository repository, MessageConverter messageConverter) {
		this.repository = repository;
		this.messageConverter = messageConverter;
	}

	@RabbitListener(queues = "notification-service.delivery-events")
	public void handle(Message message) {
		Object event = messageConverter.fromMessage(message);
		if (event instanceof DeliveryStatusUpdatedEvent updated) {
			handleDeliveryStatusUpdated(updated);
		} else {
			log.warn("Unrecognized event type on notification-service.delivery-events: {}",
					event == null ? "null" : event.getClass());
		}
	}

	private void handleDeliveryStatusUpdated(DeliveryStatusUpdatedEvent event) {
		String eventId = event.eventId().toString();
		if (repository.findByEventId(eventId).isPresent()) {
			log.debug("Duplicate delivery of event {} — already recorded, skipping.", eventId);
			return;
		}
		String message = switch (event.status()) {
			case "ASSIGNED" -> "A delivery agent has been assigned to your order #" + event.orderId() + ".";
			case "PICKED_UP" -> "Your order #" + event.orderId() + " has been picked up for delivery.";
			case "DELIVERED" -> "Your order #" + event.orderId() + " has been delivered. Enjoy!";
			default -> "Your order #" + event.orderId() + " delivery status is now " + event.status() + ".";
		};
		saveIdempotently(new NotificationRecord(eventId, "DELIVERY_" + event.status(), event.customerKeycloakId(),
				"IN_APP", message, "SENT"));
	}

	/** The real idempotency guard — see this class's javadoc. */
	private void saveIdempotently(NotificationRecord record) {
		try {
			repository.save(record);
		} catch (DuplicateKeyException e) {
			log.debug("Duplicate delivery of event {} — unique index rejected the write, skipping.",
					record.getEventId());
		}
	}

}
