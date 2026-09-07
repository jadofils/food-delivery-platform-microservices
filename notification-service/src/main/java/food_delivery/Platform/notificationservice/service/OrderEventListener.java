package food_delivery.Platform.notificationservice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import food_delivery.Platform.common.event.OrderCancelledEvent;
import food_delivery.Platform.common.event.OrderPlacedEvent;
import food_delivery.Platform.notificationservice.entity.NotificationRecord;
import food_delivery.Platform.notificationservice.repository.NotificationRecordRepository;

/**
 * Consumes {@code order-service}'s published events off {@code notification-service.order-events}
 * (RULES.md §6). RabbitMQ guarantees at-least-once delivery, not exactly-once (RULES.md §1 factor
 * 9), so a redelivery must not create two notifications for the same order event.
 *
 * <p>{@link NotificationRecordRepository#findByEventId} is checked first purely as a fast-path
 * optimization to skip an unnecessary write on the common redelivery case — it is a plain
 * find-then-save with no locking, so it is NOT by itself a correctness guarantee: two near-
 * simultaneous deliveries of the same event can both pass the check before either has saved
 * (caught by this service's own integration test). The actual idempotency guard is
 * {@code NotificationRecord.eventId}'s unique index (see {@code application.properties}'
 * {@code spring.data.mongodb.auto-index-creation=true}, without which {@code @Indexed(unique =
 * true)} is never actually enforced): a losing concurrent save is rejected by MongoDB itself with
 * {@link DuplicateKeyException}, which is caught below and treated as "already processed".
 *
 * <p>Takes the raw {@link Message} and converts it explicitly via the injected
 * {@link MessageConverter}, rather than declaring a concrete/{@code Object}-typed payload
 * parameter for {@code @RabbitListener} to convert automatically — a generic {@code Object}
 * parameter doesn't give Spring's listener adapter enough type information to know it should
 * convert at all, and it silently hands the method the untouched {@code Message} instead (caught
 * by this service's own integration test). Converting explicitly here, using the exact same
 * {@code JacksonJsonMessageConverter} the container is configured with, sidesteps that ambiguity
 * entirely.
 *
 * <p>An unhandled exception here causes the message to be nacked; after
 * {@code spring.rabbitmq.listener.simple.retry}'s attempts are exhausted, Spring AMQP's default
 * recoverer rejects it without requeue, and the queue's {@code x-dead-letter-exchange} (declared in
 * {@code RabbitConfig}) routes it to the DLQ automatically — no custom recovery code needed.
 */
@Component
public class OrderEventListener {

	private static final Logger log = LoggerFactory.getLogger(OrderEventListener.class);

	private final NotificationRecordRepository repository;
	private final MessageConverter messageConverter;

	public OrderEventListener(NotificationRecordRepository repository, MessageConverter messageConverter) {
		this.repository = repository;
		this.messageConverter = messageConverter;
	}

	@RabbitListener(queues = "notification-service.order-events")
	public void handle(Message message) {
		Object event = messageConverter.fromMessage(message);
		if (event instanceof OrderPlacedEvent placed) {
			handleOrderPlaced(placed);
		} else if (event instanceof OrderCancelledEvent cancelled) {
			handleOrderCancelled(cancelled);
		} else {
			log.warn("Unrecognized event type on notification-service.order-events: {}",
					event == null ? "null" : event.getClass());
		}
	}

	private void handleOrderPlaced(OrderPlacedEvent event) {
		String eventId = event.eventId().toString();
		if (repository.findByEventId(eventId).isPresent()) {
			log.debug("Duplicate delivery of event {} — already recorded, skipping.", eventId);
			return;
		}
		String message = "Your order #" + event.orderId() + " has been placed. Total: "
				+ event.totalAmount();
		saveIdempotently(new NotificationRecord(eventId, "ORDER_PLACED", event.customerKeycloakId(), "IN_APP",
				message, "SENT"));
	}

	private void handleOrderCancelled(OrderCancelledEvent event) {
		String eventId = event.eventId().toString();
		if (repository.findByEventId(eventId).isPresent()) {
			log.debug("Duplicate delivery of event {} — already recorded, skipping.", eventId);
			return;
		}
		String message = "Your order #" + event.orderId() + " has been cancelled.";
		saveIdempotently(new NotificationRecord(eventId, "ORDER_CANCELLED", event.customerKeycloakId(), "IN_APP",
				message, "SENT"));
	}

	/**
	 * The real idempotency guard — see this class's javadoc. A losing concurrent delivery hits
	 * {@code eventId}'s unique index and is swallowed here rather than left to fail the listener
	 * (which would otherwise trigger a pointless retry/DLQ cycle for what is actually success).
	 */
	private void saveIdempotently(NotificationRecord record) {
		try {
			repository.save(record);
		} catch (DuplicateKeyException e) {
			log.debug("Duplicate delivery of event {} — unique index rejected the write, skipping.",
					record.getEventId());
		}
	}

}
