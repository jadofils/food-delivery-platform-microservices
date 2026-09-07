package food_delivery.Platform.orderservice.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.amqp.autoconfigure.RabbitTemplateConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares the exchange order-service publishes to (RULES.md §6) — a publisher owns the exchange
 * it publishes to; each future consumer ({@code delivery-service}, {@code notification-service},
 * Sprint 5) will own its own queue and binding, not declared here.
 *
 * <p>{@code order-events.inspection} is a deliberate, temporary exception: a durable queue bound to
 * every routing key under {@code order.*}, purely so a published event is actually visible
 * somewhere (RabbitMQ's management UI, {@code http://localhost:15672}) before any real consumer
 * exists — a topic-exchange message with no bound queue is silently dropped, which would make
 * "publishing worked" impossible to see today. Remove this once {@code delivery-service}/
 * {@code notification-service} exist with their own real queues (Sprint 5).
 */
@Configuration
public class RabbitConfig {

	public static final String ORDER_EVENTS_EXCHANGE = "fdp.order-events";
	public static final String ORDER_PLACED_ROUTING_KEY = "order.placed";
	public static final String ORDER_CANCELLED_ROUTING_KEY = "order.cancelled";

	private static final String INSPECTION_QUEUE = "order-events.inspection";
	private static final String INSPECTION_ROUTING_PATTERN = "order.*";

	@Bean
	public TopicExchange orderEventsExchange() {
		return new TopicExchange(ORDER_EVENTS_EXCHANGE, true, false);
	}

	@Bean
	public Queue orderEventsInspectionQueue() {
		return new Queue(INSPECTION_QUEUE, true);
	}

	@Bean
	public Binding orderEventsInspectionBinding(Queue orderEventsInspectionQueue, TopicExchange orderEventsExchange) {
		return BindingBuilder.bind(orderEventsInspectionQueue).to(orderEventsExchange)
				.with(INSPECTION_ROUTING_PATTERN);
	}

	/**
	 * {@code JacksonJsonMessageConverter}, not the older {@code Jackson2JsonMessageConverter}:
	 * Spring AMQP 4.1 ships both, but only the former uses {@code tools.jackson} (Jackson 3, what
	 * Boot 4's own auto-configured {@code ObjectMapper} actually is) — the legacy converter still
	 * uses {@code com.fasterxml.jackson}, the same silent-mismatch trap {@code common}'s
	 * {@code MaskedFieldSerializer} already had to avoid.
	 *
	 * <p>The {@code trustedPackages} constructor argument is required, not cosmetic: without it,
	 * deserializing anything outside {@code java.util}/{@code java.lang} throws
	 * {@code IllegalArgumentException: ... is not in the trusted packages} — a real security
	 * control (a consumer must not blindly deserialize arbitrary classes named in a message header
	 * from a queue it doesn't fully trust), caught immediately by this service's own integration
	 * test.
	 */
	@Bean
	public MessageConverter jsonMessageConverter() {
		return new JacksonJsonMessageConverter("food_delivery.Platform.common.event");
	}

	/**
	 * Built via Boot's own {@link RabbitTemplateConfigurer} rather than {@code new RabbitTemplate(...)}
	 * directly — a hand-constructed template bypasses Boot's {@code spring.rabbitmq.template.*}
	 * property binding entirely, including {@code observation-enabled} (RULES.md §13): without this,
	 * the publish call carries no trace-propagation headers and never appeared as a span in
	 * order-service's own trace, confirmed empirically against a real running Zipkin. This
	 * configurer applies that (and every other {@code spring.rabbitmq.template.*} property) the same
	 * way Boot's own autoconfigured {@code RabbitTemplate} would, while still letting this service
	 * set its own {@link MessageConverter}.
	 */
	@Bean
	public RabbitTemplate rabbitTemplate(RabbitTemplateConfigurer configurer,
			org.springframework.amqp.rabbit.connection.ConnectionFactory connectionFactory,
			MessageConverter jsonMessageConverter) {
		RabbitTemplate template = new RabbitTemplate(connectionFactory);
		configurer.configure(template, connectionFactory);
		template.setMessageConverter(jsonMessageConverter);
		return template;
	}

}
