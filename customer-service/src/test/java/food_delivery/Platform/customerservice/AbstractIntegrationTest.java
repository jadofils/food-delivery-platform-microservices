package food_delivery.Platform.customerservice;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Real Postgres via Testcontainers, never H2/embedded (RULES.md §9, §1 factor 10) — every
 * integration test class extends this rather than each declaring its own container.
 *
 * <p><b>Singleton container pattern, deliberately not {@code @Testcontainers}/{@code @Container}:</b>
 * three test classes ({@code CustomerServiceApplicationTests}, {@code CustomerControllerIT},
 * {@code AddressControllerIT}) share this one static container. The per-class JUnit5 lifecycle
 * (start in {@code beforeAll}, stop in {@code afterAll}) stops the container after each class —
 * but Spring's {@code ApplicationContext} cache doesn't know that, and reuses the cached context
 * (and its already-built {@code DataSource}) for the next class with an identical configuration
 * signature, without re-invoking {@code @DynamicPropertySource} — so the second class ends up
 * connecting to a port whose container Testcontainers already tore down. Starting the container
 * exactly once in a static initializer, and never stopping it explicitly, sidesteps the whole
 * problem: the JDBC URL stays valid for every test class in the run, and Testcontainers' own Ryuk
 * reaper cleans the container up when the JVM exits — the same "singleton container" pattern
 * Testcontainers' own docs recommend for this exact multi-class-sharing case.
 */
public abstract class AbstractIntegrationTest {

	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
			.withDatabaseName("customer_db_test")
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
