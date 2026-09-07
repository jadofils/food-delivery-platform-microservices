package food_delivery.Platform.deliveryservice.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import food_delivery.Platform.deliveryservice.dto.DeliveryAssignmentResponse;
import food_delivery.Platform.deliveryservice.service.DeliveryAssignmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Every delivery assignment here was created automatically off {@code OrderPlacedEvent}
 * (RULES.md §6) — there is no {@code POST} to create one by hand. {@code delivery:read}/
 * {@code delivery:status:update} are the only two permissions seeded for this domain
 * (docker/keycloak/fdp-realm.json, held by {@code ADMIN} and {@code DELIVERY_AGENT}) — no separate
 * ownership-scoped read permission exists, so {@code GET /{id}} is readable by anyone holding
 * {@code delivery:read}, the same "broad read permission = full browsing rights" pattern
 * {@code restaurant-service}'s public browsing endpoints already use.
 */
@RestController
@RequestMapping("/api/deliveries")
@Tag(name = "Deliveries")
public class DeliveryController {

	private final DeliveryAssignmentService deliveryAssignmentService;

	public DeliveryController(DeliveryAssignmentService deliveryAssignmentService) {
		this.deliveryAssignmentService = deliveryAssignmentService;
	}

	@Operation(summary = "Get one delivery assignment by id")
	@PreAuthorize("hasAuthority('delivery:read')")
	@GetMapping("/{id}")
	public DeliveryAssignmentResponse getById(@PathVariable Long id) {
		return DeliveryAssignmentResponse.from(deliveryAssignmentService.getById(id));
	}

	@Operation(summary = "The caller's own claimed/in-progress deliveries")
	@PreAuthorize("hasAuthority('delivery:read')")
	@GetMapping("/me")
	public Page<DeliveryAssignmentResponse> listOwn(@AuthenticationPrincipal Jwt jwt, Pageable pageable) {
		return deliveryAssignmentService.listOwn(jwt, pageable).map(DeliveryAssignmentResponse::from);
	}

	@Operation(summary = "Unclaimed deliveries any agent can pick up")
	@PreAuthorize("hasAuthority('delivery:read')")
	@GetMapping("/unassigned")
	public Page<DeliveryAssignmentResponse> listUnassigned(Pageable pageable) {
		return deliveryAssignmentService.listUnassigned(pageable).map(DeliveryAssignmentResponse::from);
	}

	@Operation(summary = "Claim an unassigned delivery -- self-assigns the caller as the agent")
	@PreAuthorize("hasAuthority('delivery:status:update')")
	@PostMapping("/{id}/claim")
	public DeliveryAssignmentResponse claim(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
		return DeliveryAssignmentResponse.from(deliveryAssignmentService.claim(jwt, id));
	}

	@Operation(summary = "Mark a claimed delivery picked up -- only the assigned agent")
	@PreAuthorize("hasAuthority('delivery:status:update')")
	@PostMapping("/{id}/pickup")
	public DeliveryAssignmentResponse pickUp(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
		return DeliveryAssignmentResponse.from(deliveryAssignmentService.pickUp(jwt, id));
	}

	@Operation(summary = "Mark a picked-up delivery delivered -- only the assigned agent")
	@PreAuthorize("hasAuthority('delivery:status:update')")
	@PostMapping("/{id}/deliver")
	public DeliveryAssignmentResponse deliver(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
		return DeliveryAssignmentResponse.from(deliveryAssignmentService.deliver(jwt, id));
	}

}
