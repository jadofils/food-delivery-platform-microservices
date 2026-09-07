package food_delivery.Platform.notificationservice.entity;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A permanent record of what was sent, to whom, over which channel, and its delivery status — a
 * business entity (RULES.md §5's "notification/audit records" distinction), not an operational
 * log line. Persisted in MongoDB's {@code notification_db}, owned exclusively by this service.
 *
 * <p>{@link #eventId} carries a unique index — this is what makes consumption idempotent
 * (RULES.md §6, §1 factor 9): RabbitMQ guarantees at-least-once delivery, so the same event can
 * arrive twice; a duplicate insert attempt on the same {@code eventId} fails the unique constraint
 * instead of creating a second notification for one event.
 */
@Document(collection = "notifications")
@Getter
@Setter
@NoArgsConstructor
public class NotificationRecord {

	@Id
	private String id;

	@Indexed(unique = true)
	private String eventId;

	private String eventType;

	private String recipientKeycloakId;

	private String channel;

	private String message;

	private String status;

	private Instant createdAt;

	public NotificationRecord(String eventId, String eventType, String recipientKeycloakId, String channel,
			String message, String status) {
		this.eventId = eventId;
		this.eventType = eventType;
		this.recipientKeycloakId = recipientKeycloakId;
		this.channel = channel;
		this.message = message;
		this.status = status;
		this.createdAt = Instant.now();
	}

}
