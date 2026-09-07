package food_delivery.Platform.deliveryservice;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;

/**
 * Real Postgres AND real RabbitMQ via Testcontainers, never H2/embedded or an in-memory broker
 * (RULES.md §9, §1 factor 10) — every integration test class extends this rather than each
 * declaring its own containers.
 *
 * <p>Singleton container pattern (started once in a static initializer, never stopped) — see
 * {@code customer-service}'s identical class for why: the per-class JUnit5
 * {@code @Testcontainers} lifecycle fights Spring's context cache across multiple
 * identically-configured test classes.
 */
public abstract class AbstractIntegrationTest {

	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
			.withDatabaseName("delivery_db_test")
			.withUsername("fdp")
			.withPassword("fdp");

	static final RabbitMQContainer RABBITMQ = new RabbitMQContainer("rabbitmq:4-management-alpine");

	static {
		POSTGRES.start();
		RABBITMQ.start();
	}

	@DynamicPropertySource
	static void dynamicProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
		registry.add("spring.rabbitmq.host", RABBITMQ::getHost);
		registry.add("spring.rabbitmq.port", RABBITMQ::getAmqpPort);
		registry.add("spring.rabbitmq.username", RABBITMQ::getAdminUsername);
		registry.add("spring.rabbitmq.password", RABBITMQ::getAdminPassword);
		registry.add("fdp.security.jwk-set-uri", () -> "http://127.0.0.1:1/unused-in-tests");
		registry.add("eureka.client.enabled", () -> "false");
	}

}
