package food_delivery.Platform.notificationservice.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import food_delivery.Platform.common.security.jwt.JwtClaims;
import food_delivery.Platform.notificationservice.entity.NotificationRecord;
import food_delivery.Platform.notificationservice.repository.NotificationRecordRepository;

/** Read-only — records are only ever created by {@link OrderEventListener}, never via the REST API. */
@Service
public class NotificationQueryService {

	private final NotificationRecordRepository repository;

	public NotificationQueryService(NotificationRecordRepository repository) {
		this.repository = repository;
	}

	public Page<NotificationRecord> listOwn(Jwt jwt, Pageable pageable) {
		return repository.findByRecipientKeycloakId(JwtClaims.subject(jwt), pageable);
	}

	/** Admin-facing — gated by {@code notification:read} at the controller. */
	public Page<NotificationRecord> listAll(Pageable pageable) {
		return repository.findAll(pageable);
	}

}
