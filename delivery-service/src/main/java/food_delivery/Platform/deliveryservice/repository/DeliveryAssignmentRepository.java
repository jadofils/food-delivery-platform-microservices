package food_delivery.Platform.deliveryservice.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import food_delivery.Platform.deliveryservice.entity.DeliveryAssignment;
import food_delivery.Platform.deliveryservice.entity.DeliveryStatus;

public interface DeliveryAssignmentRepository extends JpaRepository<DeliveryAssignment, Long> {

	/** Fast-path idempotency pre-check — see {@link DeliveryAssignment}'s class comment for why the
	 * unique index on {@code order_id} is the real guard, not this. */
	boolean existsByOrderId(Long orderId);

	Optional<DeliveryAssignment> findByOrderId(Long orderId);

	/** Used by tests to prove no duplicate row was created for one order (unique index notwithstanding). */
	long countByOrderId(Long orderId);

	/** An agent's own claimed/in-progress deliveries — {@code GET /api/deliveries/me}. */
	Page<DeliveryAssignment> findByAssignedAgentKeycloakId(String assignedAgentKeycloakId, Pageable pageable);

	/** Unclaimed deliveries any agent can pick up — {@code GET /api/deliveries/unassigned}. */
	Page<DeliveryAssignment> findByStatus(DeliveryStatus status, Pageable pageable);

}
