package food_delivery.Platform.notificationservice.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import food_delivery.Platform.notificationservice.dto.NotificationResponse;
import food_delivery.Platform.notificationservice.service.NotificationQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * {@code /me} is self-service — any authenticated caller may read their own notification history,
 * no permission beyond a valid token required, matching the same "authenticated is enough for
 * self-service" pattern {@code customer-service}/{@code restaurant-service} already established.
 * {@code notification:read} (only the seeded {@code ADMIN} account holds it — see
 * docker/keycloak/fdp-realm.json) gates the admin-wide listing instead, since no non-admin seeded
 * role carries it — there's no ambiguity to resolve here the way there was for order-service's
 * admin-listing gap.
 */
@RestController
@RequestMapping("/api/notifications")
@Tag(name = "Notifications")
public class NotificationController {

	private final NotificationQueryService queryService;

	public NotificationController(NotificationQueryService queryService) {
		this.queryService = queryService;
	}

	@Operation(summary = "List the caller's own notification history")
	@GetMapping("/me")
	public Page<NotificationResponse> listOwn(@AuthenticationPrincipal Jwt jwt, Pageable pageable) {
		return queryService.listOwn(jwt, pageable).map(NotificationResponse::from);
	}

	@Operation(summary = "Admin: list every notification/audit record — requires notification:read")
	@PreAuthorize("hasAuthority('notification:read')")
	@GetMapping
	public Page<NotificationResponse> listAll(Pageable pageable) {
		return queryService.listAll(pageable).map(NotificationResponse::from);
	}

}
