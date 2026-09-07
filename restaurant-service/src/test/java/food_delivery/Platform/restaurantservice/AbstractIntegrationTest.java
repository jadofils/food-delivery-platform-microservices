package food_delivery.Platform.restaurantservice;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Real Postgres and Redis via Testcontainers, never H2/embedded or an in-memory cache (RULES.md
 * §9, §1 factor 10, §12) — every integration test class extends this rather than each declaring
 * its own containers.
 *
 * <p>Singleton container pattern (started once in a static initializer, never stopped) — see
 * {@code customer-service}'s identical class for why: the per-class JUnit5
 * {@code @Testcontainers} lifecycle fights Spring's context cache across multiple
 * identically-configured test classes, so this sidesteps it entirely rather than rediscovering the
 * same bug here.
 *
 * <p>{@code GenericContainer}, not a dedicated Testcontainers Redis module — Testcontainers ships
 * first-class modules for Postgres/MongoDB/RabbitMQ but not Redis; a plain container running the
 * same {@code redis:7-alpine} image {@code docker-compose.yml} uses is the standard approach.
 */
public abstract class AbstractIntegrationTest {

	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
			.withDatabaseName("restaurant_db_test")
			.withUsername("fdp")
			.withPassword("fdp");

	static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
			.withExposedPorts(6379);

	static {
		POSTGRES.start();
		REDIS.start();
	}

	@DynamicPropertySource
	static void datasourceProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
		registry.add("spring.data.redis.host", REDIS::getHost);
		registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
		registry.add("spring.data.redis.password", () -> "");
		registry.add("fdp.security.jwk-set-uri", () -> "http://127.0.0.1:1/unused-in-tests");
		registry.add("eureka.client.enabled", () -> "false");
	}

}
