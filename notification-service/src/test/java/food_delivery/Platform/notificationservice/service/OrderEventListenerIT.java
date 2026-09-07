package food_delivery.Platform.notificationservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;

import food_delivery.Platform.common.event.OrderCancelledEvent;
import food_delivery.Platform.common.event.OrderPlacedEvent;
import food_delivery.Platform.notificationservice.AbstractIntegrationTest;
import food_delivery.Platform.notificationservice.repository.NotificationRecordRepository;

/**
 * Publishes real events onto the real exchange/queue topology {@code RabbitConfig} declares — this
 * proves the consumer side end to end, not just the listener method in isolation.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OrderEventListenerIT extends AbstractIntegrationTest {

	@Autowired
	private RabbitTemplate rabbitTemplate;

	@Autowired
	private NotificationRecordRepository repository;

	@Autowired
	private MockMvc mockMvc;

	private static JwtRequestPostProcessor customer(String sub) {
		return jwt().jwt(builder -> builder.subject(sub).claim("email", sub + "@fdp.test"))
				.authorities(new SimpleGrantedAuthority("order:read"));
	}

	private static JwtRequestPostProcessor admin(String sub) {
		return jwt().jwt(builder -> builder.subject(sub).claim("email", "admin@fdp.test"))
				.authorities(new SimpleGrantedAuthority("notification:read"));
	}

	@Test
	void orderPlacedEvent_producesANotificationRecord_visibleViaOwnHistory() throws Exception {
		String customerSub = "kc-notify-1";
		OrderPlacedEvent event = new OrderPlacedEvent(UUID.randomUUID(), 1L, customerSub, 1L, 1L,
				new BigDecimal("11.00"), List.of(new OrderPlacedEvent.Item(1L, "Brochette", new BigDecimal("5.50"), 2)),
				Instant.now());

		rabbitTemplate.convertAndSend("fdp.order-events", "order.placed", event);

		waitUntil(() -> repository.findByEventId(event.eventId().toString()).isPresent());

		mockMvc.perform(get("/api/notifications/me").with(customer(customerSub)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].eventType").value("ORDER_PLACED"))
				.andExpect(jsonPath("$.content[0].message").value(org.hamcrest.Matchers.containsString("11.00")));
	}

	@Test
	void orderCancelledEvent_producesANotificationRecord() throws Exception {
		String customerSub = "kc-notify-2";
		OrderCancelledEvent event = new OrderCancelledEvent(UUID.randomUUID(), 2L, customerSub, 1L, Instant.now());

		rabbitTemplate.convertAndSend("fdp.order-events", "order.cancelled", event);

		waitUntil(() -> repository.findByEventId(event.eventId().toString()).isPresent());

		mockMvc.perform(get("/api/notifications/me").with(customer(customerSub)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].eventType").value("ORDER_CANCELLED"));
	}

	@Test
	void redeliveredEvent_isIdempotent_onlyOneRecordCreated() throws Exception {
		String customerSub = "kc-notify-3";
		UUID eventId = UUID.randomUUID();
		OrderPlacedEvent event = new OrderPlacedEvent(eventId, 3L, customerSub, 1L, 1L, new BigDecimal("5.50"),
				List.of(new OrderPlacedEvent.Item(1L, "Brochette", new BigDecimal("5.50"), 1)), Instant.now());

		rabbitTemplate.convertAndSend("fdp.order-events", "order.placed", event);
		waitUntil(() -> repository.findByEventId(eventId.toString()).isPresent());

		// Same event, redelivered (simulating RabbitMQ's at-least-once guarantee).
		rabbitTemplate.convertAndSend("fdp.order-events", "order.placed", event);
		Thread.sleep(500);

		// Scoped to this run's own (freshly random) eventId, not the shared customerSub literal --
		// counting by customerSub picked up unrelated leftover records from this machine's
		// Testcontainers volumes surviving across separate JVM runs, an environment quirk unrelated
		// to the idempotency behavior actually under test here.
		assertThat(repository.countByEventId(eventId.toString())).isEqualTo(1);
	}

	@Test
	void plainCustomer_cannotListAllNotifications_butAdminCan() throws Exception {
		mockMvc.perform(get("/api/notifications").with(customer("kc-notify-no-admin")))
				.andExpect(status().isForbidden());

		mockMvc.perform(get("/api/notifications").with(admin("kc-notify-admin")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content").isArray());
	}

	private void waitUntil(java.util.function.BooleanSupplier condition) throws InterruptedException {
		for (int i = 0; i < 40; i++) {
			if (condition.getAsBoolean()) {
				return;
			}
			Thread.sleep(250);
		}
		throw new AssertionError("Condition not met within timeout");
	}

}
