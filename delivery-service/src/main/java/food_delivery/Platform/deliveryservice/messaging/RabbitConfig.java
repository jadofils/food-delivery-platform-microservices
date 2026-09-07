package food_delivery.Platform.deliveryservice.messaging;

import java.util.Map;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.amqp.autoconfigure.RabbitTemplateConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * This service is both a consumer and a publisher (RULES.md §6): it consumes {@code order-service}'s
 * {@code fdp.order-events} to auto-create delivery assignments, and it owns/publishes
 * {@code fdp.delivery-events} for {@code notification-service} to consume in turn. Per RULES.md §6,
 * a publisher owns the exchange it publishes to and a consumer owns its own queue/DLQ — never a
 * shared queue between consumers.
 */
@Configuration
public class RabbitConfig {

	private static final String ORDER_EVENTS_EXCHANGE = "fdp.order-events";
	private static final String QUEUE = "delivery-service.order-events";
	private static final String DLX = "delivery-service.dlx";
	private static final String DLQ = "delivery-service.order-events.dlq";
	private static final String DLQ_ROUTING_KEY = "dlq";

	public static final String DELIVERY_EVENTS_EXCHANGE = "fdp.delivery-events";
	public static final String DELIVERY_STATUS_UPDATED_ROUTING_KEY = "delivery.status-updated";

	/**
	 * Re-declares {@code fdp.order-events} with the exact same properties {@code order-service} used
	 * (durable, non-autodelete) — a safe, idempotent AMQP declaration, necessary so this service can
	 * start (and bind its own queue) even if it happens to come up before {@code order-service} does.
	 */
	@Bean
	public TopicExchange orderEventsExchange() {
		return new TopicExchange(ORDER_EVENTS_EXCHANGE, true, false);
	}

	@Bean
	public DirectExchange deliveryServiceDeadLetterExchange() {
		return new DirectExchange(DLX, true, false);
	}

	@Bean
	public Queue deliveryServiceDeadLetterQueue() {
		return new Queue(DLQ, true);
	}

	@Bean
	public Binding deliveryServiceDeadLetterBinding(Queue deliveryServiceDeadLetterQueue,
			DirectExchange deliveryServiceDeadLetterExchange) {
		return BindingBuilder.bind(deliveryServiceDeadLetterQueue).to(deliveryServiceDeadLetterExchange)
				.with(DLQ_ROUTING_KEY);
	}

	/**
	 * {@code x-dead-letter-exchange}/{@code x-dead-letter-routing-key}: a message rejected without
	 * requeue (once {@code spring.rabbitmq.listener.simple.retry}'s attempts are exhausted,
	 * application.properties) is routed here automatically by the broker itself — no custom recovery
	 * code needed for RULES.md §6's "every consumer queue has a dead-letter queue".
	 */
	@Bean
	public Queue orderEventsQueue() {
		return QueueBuilder.durable(QUEUE)
				.withArguments(Map.of(
						"x-dead-letter-exchange", DLX,
						"x-dead-letter-routing-key", DLQ_ROUTING_KEY))
				.build();
	}

	@Bean
	public Binding orderEventsBinding(Queue orderEventsQueue, TopicExchange orderEventsExchange) {
		return BindingBuilder.bind(orderEventsQueue).to(orderEventsExchange).with("order.#");
	}

	/** The exchange this service owns and publishes {@code DeliveryStatusUpdatedEvent} to. */
	@Bean
	public TopicExchange deliveryEventsExchange() {
		return new TopicExchange(DELIVERY_EVENTS_EXCHANGE, true, false);
	}

	/**
	 * Same {@code JacksonJsonMessageConverter} choice/reasoning as every other service's own config —
	 * Spring AMQP 4.1 ships both a Jackson 3 (correct, matches Boot 4's own {@code ObjectMapper}) and
	 * a legacy Jackson 2 converter; the latter silently fails to (de)serialize records the way this
	 * codebase expects. {@code trustedPackages} is required, not cosmetic — deserializing anything
	 * outside it throws, a real security control against blindly deserializing an arbitrary class
	 * named in a message header.
	 */
	@Bean
	public MessageConverter jsonMessageConverter() {
		return new JacksonJsonMessageConverter("food_delivery.Platform.common.event");
	}

	/**
	 * Built via Boot's own {@link RabbitTemplateConfigurer}, not {@code new RabbitTemplate(...)}
	 * directly — a hand-constructed template bypasses {@code spring.rabbitmq.template.*} property
	 * binding entirely, including {@code observation-enabled} (RULES.md §13), which is what makes
	 * this service's publish continue the trace that led to it rather than starting a disconnected
	 * one. Confirmed the hard way on {@code order-service} first (see its own {@code RabbitConfig}).
	 */
	@Bean
	public RabbitTemplate rabbitTemplate(RabbitTemplateConfigurer configurer, ConnectionFactory connectionFactory,
			MessageConverter jsonMessageConverter) {
		RabbitTemplate template = new RabbitTemplate(connectionFactory);
		configurer.configure(template, connectionFactory);
		template.setMessageConverter(jsonMessageConverter);
		return template;
	}

}
