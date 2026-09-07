package food_delivery.Platform.notificationservice.dto;

import java.time.Instant;

import food_delivery.Platform.notificationservice.entity.NotificationRecord;

public record NotificationResponse(
		String id,
		String eventType,
		String channel,
		String message,
		String status,
		Instant createdAt) {

	public static NotificationResponse from(NotificationRecord record) {
		return new NotificationResponse(record.getId(), record.getEventType(), record.getChannel(),
				record.getMessage(), record.getStatus(), record.getCreatedAt());
	}

}
