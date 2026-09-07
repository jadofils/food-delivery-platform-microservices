package food_delivery.Platform.deliveryservice.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import food_delivery.Platform.common.error.ConflictException;
import food_delivery.Platform.common.error.ForbiddenException;
import food_delivery.Platform.common.error.ResourceNotFoundException;
import food_delivery.Platform.common.security.jwt.JwtClaims;
import food_delivery.Platform.deliveryservice.entity.DeliveryAssignment;
import food_delivery.Platform.deliveryservice.entity.DeliveryStatus;
import food_delivery.Platform.deliveryservice.messaging.DeliveryEventPublisher;
import food_delivery.Platform.deliveryservice.repository.DeliveryAssignmentRepository;

/**
 * Every write here is a forward-only status transition by the assigned agent (RULES.md — no
 * REST-driven cancellation; that only ever happens via {@code OrderCancelledEvent}, see
 * {@link food_delivery.Platform.deliveryservice.messaging.OrderEventListener}). Each successful
 * transition publishes {@code DeliveryStatusUpdatedEvent} — see {@link DeliveryEventPublisher}.
 */
@Service
public class DeliveryAssignmentService {

	private final DeliveryAssignmentRepository repository;
	private final DeliveryEventPublisher eventPublisher;

	public DeliveryAssignmentService(DeliveryAssignmentRepository repository, DeliveryEventPublisher eventPublisher) {
		this.repository = repository;
		this.eventPublisher = eventPublisher;
	}

	@Transactional(readOnly = true)
	public DeliveryAssignment getById(Long id) {
		return findOrThrow(id);
	}

	/**
	 * Self-service lookup for the customer who placed the order — {@code GET
	 * /api/deliveries/by-order/{orderId}}, no {@code delivery:read} required (no such permission is
	 * seeded for {@code CUSTOMER}). {@code order-service} calls this with the customer's own relayed
	 * token to enrich its own order-detail response with live delivery status.
	 *
	 * <p>404, not 403, when the caller isn't the order's owner — the same "don't leak whether the
	 * resource exists" pattern {@code order-service}'s own {@code findOwnOrThrow} already uses for
	 * an equivalent self-service lookup.
	 */
	@Transactional(readOnly = true)
	public DeliveryAssignment getByOrderIdForCustomer(Jwt jwt, Long orderId) {
		DeliveryAssignment assignment = repository.findByOrderId(orderId)
				.orElseThrow(() -> new ResourceNotFoundException("No delivery assignment for order " + orderId));
		if (!JwtClaims.subject(jwt).equals(assignment.getCustomerKeycloakId())) {
			throw new ResourceNotFoundException("No delivery assignment for order " + orderId);
		}
		return assignment;
	}

	/** Any delivery-agent's own claimed/in-progress deliveries — {@code GET /api/deliveries/me}. */
	@Transactional(readOnly = true)
	public Page<DeliveryAssignment> listOwn(Jwt jwt, Pageable pageable) {
		return repository.findByAssignedAgentKeycloakId(JwtClaims.subject(jwt), pageable);
	}

	/** Unclaimed deliveries any agent can pick up — {@code GET /api/deliveries/unassigned}. */
	@Transactional(readOnly = true)
	public Page<DeliveryAssignment> listUnassigned(Pageable pageable) {
		return repository.findByStatus(DeliveryStatus.PENDING, pageable);
	}

	/** Self-assigns the caller as the delivery agent. 409 if it's not in {@code PENDING}. */
	@Transactional
	public DeliveryAssignment claim(Jwt jwt, Long id) {
		DeliveryAssignment assignment = findOrThrow(id);
		if (assignment.getStatus() != DeliveryStatus.PENDING) {
			throw new ConflictException("Delivery " + id + " is not available to claim (status "
					+ assignment.getStatus() + ").");
		}
		assignment.setAssignedAgentKeycloakId(JwtClaims.subject(jwt));
		assignment.setStatus(DeliveryStatus.ASSIGNED);
		eventPublisher.publishStatusUpdated(assignment);
		return assignment;
	}

	@Transactional
	public DeliveryAssignment pickUp(Jwt jwt, Long id) {
		return transition(jwt, id, DeliveryStatus.ASSIGNED, DeliveryStatus.PICKED_UP);
	}

	@Transactional
	public DeliveryAssignment deliver(Jwt jwt, Long id) {
		return transition(jwt, id, DeliveryStatus.PICKED_UP, DeliveryStatus.DELIVERED);
	}

	private DeliveryAssignment transition(Jwt jwt, Long id, DeliveryStatus expectedCurrent, DeliveryStatus next) {
		DeliveryAssignment assignment = findOrThrow(id);
		String caller = JwtClaims.subject(jwt);
		if (!caller.equals(assignment.getAssignedAgentKeycloakId())) {
			throw new ForbiddenException("Delivery " + id + " is not assigned to you.");
		}
		if (assignment.getStatus() != expectedCurrent) {
			throw new ConflictException("Delivery " + id + " cannot move to " + next + " from status "
					+ assignment.getStatus() + ".");
		}
		assignment.setStatus(next);
		eventPublisher.publishStatusUpdated(assignment);
		return assignment;
	}

	private DeliveryAssignment findOrThrow(Long id) {
		return repository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("No delivery assignment with id " + id));
	}

}
