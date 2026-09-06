package food_delivery.Platform.restaurantservice;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Real Postgres via Testcontainers, never H2/embedded (RULES.md §9, §1 factor 10) — every
 * integration test class extends this rather than each declaring its own container.
 *
 * <p>Singleton container pattern (started once in a static initializer, never stopped) — see
 * {@code customer-service}'s identical class for why: the per-class JUnit5
 * {@code @Testcontainers} lifecycle fights Spring's context cache across multiple
 * identically-configured test classes, so this sidesteps it entirely rather than rediscovering the
 * same bug here.
 */
public abstract class AbstractIntegrationTest {

	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
			.withDatabaseName("restaurant_db_test")
			.withUsername("fdp")
			.withPassword("fdp");

	static {
		POSTGRES.start();
	}

	@DynamicPropertySource
	static void datasourceProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
		registry.add("fdp.security.jwk-set-uri", () -> "http://127.0.0.1:1/unused-in-tests");
		registry.add("eureka.client.enabled", () -> "false");
	}

}
