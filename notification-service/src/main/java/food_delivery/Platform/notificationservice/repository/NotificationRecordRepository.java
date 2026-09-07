package food_delivery.Platform.notificationservice.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import food_delivery.Platform.notificationservice.entity.NotificationRecord;

public interface NotificationRecordRepository extends MongoRepository<NotificationRecord, String> {

	Page<NotificationRecord> findByRecipientKeycloakId(String recipientKeycloakId, Pageable pageable);

	/** The idempotency check itself — see {@link NotificationRecord}'s class comment. */
	Optional<NotificationRecord> findByEventId(String eventId);

	/** Used by tests to prove no duplicate row was created for one event (unique index notwithstanding). */
	long countByEventId(String eventId);

}
