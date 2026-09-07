package food_delivery.Platform.notificationservice.messaging;

import java.util.Map;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The consumer side of RULES.md §6's messaging topology — {@code order-service} owns the
 * exchange, every consumer owns its own queue, binding, and dead-letter queue (never shared
 * between consumers, so one consumer's backlog/poison message can never affect another's). This
 * service re-declares {@code fdp.order-events} with the exact same properties order-service used
 * (durable, non-autodelete) — a safe, idempotent AMQP declaration, and necessary so this service
 * can start (and bind its own queue) even if it happens to come up before order-service does.
 */
@Configuration
public class RabbitConfig {

	private static final String ORDER_EVENTS_EXCHANGE = "fdp.order-events";
	private static final String QUEUE = "notification-service.order-events";
	private static final String DLX = "notification-service.dlx";
	private static final String DLQ = "notification-service.order-events.dlq";
	private static final String DLQ_ROUTING_KEY = "dlq";

	@Bean
	public TopicExchange orderEventsExchange() {
		return new TopicExchange(ORDER_EVENTS_EXCHANGE, true, false);
	}

	@Bean
	public DirectExchange notificationDeadLetterExchange() {
		return new DirectExchange(DLX, true, false);
	}

	@Bean
	public Queue notificationDeadLetterQueue() {
		return new Queue(DLQ, true);
	}

	@Bean
	public Binding notificationDeadLetterBinding(Queue notificationDeadLetterQueue,
			DirectExchange notificationDeadLetterExchange) {
		return BindingBuilder.bind(notificationDeadLetterQueue).to(notificationDeadLetterExchange)
				.with(DLQ_ROUTING_KEY);
	}

	/**
	 * {@code x-dead-letter-exchange}/{@code x-dead-letter-routing-key}: a message rejected without
	 * requeue (which is exactly what happens once {@code spring.rabbitmq.listener.simple.retry}'s
	 * attempts are exhausted, application.properties) is routed here automatically by the broker
	 * itself — no custom recovery code needed for RULES.md §6's "every consumer queue has a
	 * dead-letter queue".
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

	/**
	 * Same {@code JacksonJsonMessageConverter} choice/reasoning as order-service's own config —
	 * used both for Boot's auto-configured {@code RabbitTemplate} (which picks up a single
	 * {@code MessageConverter} bean automatically) and explicitly by {@code OrderEventListener}
	 * itself, which converts the raw {@code Message} it receives using this exact bean rather than
	 * relying on {@code @RabbitListener} to convert automatically — see that class's javadoc for
	 * why a generic {@code Object}-typed listener parameter doesn't get automatic conversion
	 * (caught by this service's own integration test).
	 */
	@Bean
	public MessageConverter jsonMessageConverter() {
		return new JacksonJsonMessageConverter("food_delivery.Platform.common.event");
	}

}
