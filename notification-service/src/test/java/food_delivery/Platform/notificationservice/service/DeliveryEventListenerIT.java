package food_delivery.Platform.notificationservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
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

import food_delivery.Platform.common.event.DeliveryStatusUpdatedEvent;
import food_delivery.Platform.notificationservice.AbstractIntegrationTest;
import food_delivery.Platform.notificationservice.repository.NotificationRecordRepository;

/**
 * The pair to {@code OrderEventListenerIT} — same reasoning, different exchange/queue
 * ({@code fdp.delivery-events} / {@code notification-service.delivery-events}).
 */
@SpringBootTest
@AutoConfigureMockMvc
class DeliveryEventListenerIT extends AbstractIntegrationTest {

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

	@Test
	void deliveryStatusUpdatedEvent_producesANotificationRecord_visibleViaOwnHistory() throws Exception {
		String customerSub = "kc-delivery-notify-1";
		DeliveryStatusUpdatedEvent event = new DeliveryStatusUpdatedEvent(UUID.randomUUID(), 10L, 1L, customerSub,
				"PICKED_UP", "kc-agent-x", Instant.now());

		rabbitTemplate.convertAndSend("fdp.delivery-events", "delivery.status-updated", event);

		waitUntil(() -> repository.findByEventId(event.eventId().toString()).isPresent());

		mockMvc.perform(get("/api/notifications/me").with(customer(customerSub)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].eventType").value("DELIVERY_PICKED_UP"))
				.andExpect(jsonPath("$.content[0].message").value(org.hamcrest.Matchers.containsString("picked up")));
	}

	@Test
	void redeliveredDeliveryEvent_isIdempotent_onlyOneRecordCreated() throws Exception {
		UUID eventId = UUID.randomUUID();
		DeliveryStatusUpdatedEvent event = new DeliveryStatusUpdatedEvent(eventId, 11L, 2L, "kc-delivery-notify-2",
				"DELIVERED", "kc-agent-y", Instant.now());

		rabbitTemplate.convertAndSend("fdp.delivery-events", "delivery.status-updated", event);
		waitUntil(() -> repository.findByEventId(eventId.toString()).isPresent());

		// Same event, redelivered (simulating RabbitMQ's at-least-once guarantee).
		rabbitTemplate.convertAndSend("fdp.delivery-events", "delivery.status-updated", event);
		Thread.sleep(500);

		assertThat(repository.countByEventId(eventId.toString())).isEqualTo(1);
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
