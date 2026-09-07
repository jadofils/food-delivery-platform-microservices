package food_delivery.Platform.notificationservice;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.RabbitMQContainer;

/**
 * Real MongoDB AND real RabbitMQ via Testcontainers, never embedded Mongo or an in-memory broker
 * (RULES.md §9, §1 factor 10). Singleton container pattern (started once, never stopped) — see
 * {@code customer-service}'s identical class for why.
 */
public abstract class AbstractIntegrationTest {

	static final MongoDBContainer MONGODB = new MongoDBContainer("mongo:7");
	static final RabbitMQContainer RABBITMQ = new RabbitMQContainer("rabbitmq:4-management-alpine");

	static {
		MONGODB.start();
		RABBITMQ.start();
	}

	@DynamicPropertySource
	static void dynamicProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.data.mongodb.uri", () -> MONGODB.getReplicaSetUrl("notification_db_test"));
		registry.add("spring.rabbitmq.host", RABBITMQ::getHost);
		registry.add("spring.rabbitmq.port", RABBITMQ::getAmqpPort);
		registry.add("spring.rabbitmq.username", RABBITMQ::getAdminUsername);
		registry.add("spring.rabbitmq.password", RABBITMQ::getAdminPassword);
		registry.add("fdp.security.jwk-set-uri", () -> "http://127.0.0.1:1/unused-in-tests");
		registry.add("eureka.client.enabled", () -> "false");
	}

}
