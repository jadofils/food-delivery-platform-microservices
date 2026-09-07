package food_delivery.Platform.deliveryservice.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;

import food_delivery.Platform.common.event.OrderPlacedEvent;
import food_delivery.Platform.deliveryservice.AbstractIntegrationTest;
import food_delivery.Platform.deliveryservice.repository.DeliveryAssignmentRepository;

/**
 * Every delivery assignment under test here is created the same way one ever is in production: by
 * publishing a real {@code OrderPlacedEvent} onto the real exchange and waiting for the consumer to
 * process it — there is no REST endpoint to create one directly (see {@code DeliveryController}'s
 * class comment).
 */
@SpringBootTest
@AutoConfigureMockMvc
class DeliveryControllerIT extends AbstractIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private RabbitTemplate rabbitTemplate;

	@Autowired
	private DeliveryAssignmentRepository repository;

	/** delivery-agent@fdp.test's actual role set. */
	private static JwtRequestPostProcessor agent(String sub) {
		return jwt().jwt(builder -> builder.subject(sub).claim("email", sub + "@fdp.test"))
				.authorities(new SimpleGrantedAuthority("delivery:status:update"),
						new SimpleGrantedAuthority("delivery:read"), new SimpleGrantedAuthority("order:read"));
	}

	/** customer@fdp.test's actual role set -- lacks delivery:read/delivery:status:update entirely. */
	private static JwtRequestPostProcessor customer(String sub) {
		return jwt().jwt(builder -> builder.subject(sub).claim("email", sub + "@fdp.test"))
				.authorities(new SimpleGrantedAuthority("order:create"), new SimpleGrantedAuthority("order:read"));
	}

	private Long placeOrderAndWaitForAssignment(long orderId) throws Exception {
		return placeOrderAndWaitForAssignment(orderId, "kc-delivery-target-customer");
	}

	private Long placeOrderAndWaitForAssignment(long orderId, String customerKeycloakId) throws Exception {
		OrderPlacedEvent event = new OrderPlacedEvent(UUID.randomUUID(), orderId, customerKeycloakId, 1L,
				1L, new BigDecimal("5.50"),
				List.of(new OrderPlacedEvent.Item(1L, "Brochette", new BigDecimal("5.50"), 1)), Instant.now());
		rabbitTemplate.convertAndSend("fdp.order-events", "order.placed", event);
		waitUntil(() -> repository.findByOrderId(orderId).isPresent());
		return repository.findByOrderId(orderId).orElseThrow().getId();
	}

	@Test
	void unauthenticatedRequest_isRejectedWith401() throws Exception {
		mockMvc.perform(get("/api/deliveries/1")).andExpect(status().isUnauthorized());
	}

	@Test
	void customer_cannotBrowseOrClaimDeliveries() throws Exception {
		Long id = placeOrderAndWaitForAssignment(6001L);

		mockMvc.perform(get("/api/deliveries/" + id).with(customer("kc-no-delivery-access")))
				.andExpect(status().isForbidden());
		mockMvc.perform(post("/api/deliveries/" + id + "/claim").with(customer("kc-no-delivery-access")))
				.andExpect(status().isForbidden());
	}

	@Test
	void claimPickupDeliver_fullLifecycle() throws Exception {
		Long id = placeOrderAndWaitForAssignment(6002L);
		String agentSub = "kc-agent-lifecycle";

		mockMvc.perform(get("/api/deliveries/" + id).with(agent(agentSub)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("PENDING"));

		mockMvc.perform(post("/api/deliveries/" + id + "/claim").with(agent(agentSub)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ASSIGNED"))
				.andExpect(jsonPath("$.assignedAgentKeycloakId").value(agentSub));

		mockMvc.perform(post("/api/deliveries/" + id + "/pickup").with(agent(agentSub)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("PICKED_UP"));

		mockMvc.perform(post("/api/deliveries/" + id + "/deliver").with(agent(agentSub)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("DELIVERED"));

		mockMvc.perform(get("/api/deliveries/me").with(agent(agentSub)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content").isArray());
	}

	@Test
	void claim_whenAlreadyClaimed_returnsConflict() throws Exception {
		Long id = placeOrderAndWaitForAssignment(6003L);
		mockMvc.perform(post("/api/deliveries/" + id + "/claim").with(agent("kc-agent-first")))
				.andExpect(status().isOk());

		mockMvc.perform(post("/api/deliveries/" + id + "/claim").with(agent("kc-agent-second")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("CONFLICT"));
	}

	@Test
	void pickup_byAnotherAgent_returnsForbidden() throws Exception {
		Long id = placeOrderAndWaitForAssignment(6004L);
		mockMvc.perform(post("/api/deliveries/" + id + "/claim").with(agent("kc-agent-owner")))
				.andExpect(status().isOk());

		mockMvc.perform(post("/api/deliveries/" + id + "/pickup").with(agent("kc-agent-not-owner")))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error").value("FORBIDDEN"));
	}

	@Test
	void pickup_beforeClaiming_returnsForbidden() throws Exception {
		Long id = placeOrderAndWaitForAssignment(6005L);

		// Never claimed -- assignedAgentKeycloakId is null, so no caller can ever equal it.
		mockMvc.perform(post("/api/deliveries/" + id + "/pickup").with(agent("kc-agent-impatient")))
				.andExpect(status().isForbidden());
	}

	@Test
	void listUnassigned_showsThePendingDelivery() throws Exception {
		placeOrderAndWaitForAssignment(6006L);

		mockMvc.perform(get("/api/deliveries/unassigned").with(agent("kc-agent-browsing")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content").isArray());
	}

	@Test
	void getByOrderId_ownCustomer_seesLiveDeliveryStatus() throws Exception {
		long orderId = 6007L;
		String customerSub = "kc-tracking-owner";
		placeOrderAndWaitForAssignment(orderId, customerSub);

		mockMvc.perform(get("/api/deliveries/by-order/" + orderId).with(customer(customerSub)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("PENDING"));
	}

	@Test
	void getByOrderId_anotherCustomer_isRejectedWith404() throws Exception {
		long orderId = 6008L;
		placeOrderAndWaitForAssignment(orderId, "kc-tracking-owner-2");

		// Same "don't leak whether the resource exists" pattern as order-service's own /me routes --
		// a customer who isn't the order's owner gets 404, not 403.
		mockMvc.perform(get("/api/deliveries/by-order/" + orderId).with(customer("kc-not-the-owner")))
				.andExpect(status().isNotFound());
	}

	@Test
	void getByOrderId_noAssignmentYet_isNotFound() throws Exception {
		mockMvc.perform(get("/api/deliveries/by-order/999999").with(customer("kc-tracking-owner-3")))
				.andExpect(status().isNotFound());
	}

	private void waitUntil(BooleanSupplier condition) throws InterruptedException {
		for (int i = 0; i < 40; i++) {
			if (condition.getAsBoolean()) {
				return;
			}
			Thread.sleep(250);
		}
		throw new AssertionError("Condition not met within timeout");
	}

}
