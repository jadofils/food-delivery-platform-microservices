package food_delivery.Platform.apigateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * {@code webEnvironment} stays the (default) mock one, not a real running server — Spring Cloud
 * Gateway's own routing filter still makes a real outbound HTTP call when proxying regardless of
 * how the *inbound* request arrives (that call is what "no instance available" below exercises),
 * so nothing about routing/rate-limiting behavior is faked by testing this way. What mock mode
 * buys is {@link org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers#mockJwt()}
 * actually working: it injects a pre-authenticated principal by mutating the exchange in-process,
 * which only holds together when request dispatch stays in-process — hitting a real bound socket
 * (as {@code webEnvironment = RANDOM_PORT} would) sends the request over the wire as a real
 * customer would, where no fake-principal injection is possible and a real, JWKS-signed token
 * would be required instead.
 *
 * <p>Real Redis via Testcontainers (RULES.md §9), not embedded/mocked — same
 * {@code GenericContainer} pattern {@code restaurant-service}'s own {@code AbstractIntegrationTest}
 * uses (Testcontainers has no dedicated Redis module). {@code eureka.client.enabled=false}: this
 * class tests the gateway's own security and rate-limiting behavior, not real cross-service
 * routing — that is exercised manually against the live running stack (see
 * {@code docs/services/api-gateway.md}), the same split every other service's own IT class draws
 * between "fast, deterministic own-logic tests" and "live verification of the real integration."
 */
@SpringBootTest
class ApiGatewaySecurityIT {

	@Autowired
	private ApplicationContext context;

	private WebTestClient webTestClient;

	static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
			.withExposedPorts(6379);

	static {
		REDIS.start();
	}

	@DynamicPropertySource
	static void dynamicProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.data.redis.host", REDIS::getHost);
		registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
		registry.add("spring.data.redis.password", () -> "");
		registry.add("fdp.security.jwk-set-uri", () -> "http://127.0.0.1:1/unused-in-tests");
		registry.add("eureka.client.enabled", () -> "false");
	}

	@BeforeEach
	void setUp() {
		// .apply(springSecurity()) is the WebFlux equivalent of MockMvc's own
		// .apply(springSecurity()) that @AutoConfigureMockMvc wires in automatically for every
		// other service's tests -- without it, mockJwt() below has nothing to inject into and the
		// real (unmodified) reactive security filter chain runs, rejecting every request as
		// unauthenticated regardless of mutateWith(mockJwt()...).
		webTestClient = WebTestClient.bindToApplicationContext(context).apply(springSecurity()).build();
	}

	@Test
	void unauthenticatedRequest_isRejectedWith401() {
		webTestClient.get().uri("/api/customers/me")
				.exchange()
				.expectStatus().isUnauthorized()
				.expectBody()
				.jsonPath("$.error").isEqualTo("UNAUTHORIZED")
				.jsonPath("$.status").isEqualTo(401);
	}

	@Test
	void authenticatedRequest_passesTheSecurityFilterChain() {
		// eureka.client.enabled=false means there is no real customer-service instance for this
		// route to resolve to -- passing security is proven by NOT getting 401 here, regardless of
		// whatever downstream routing failure follows (that failure mode is not this test's
		// concern; real routing is verified live against the actual running stack).
		webTestClient.mutateWith(mockJwt().jwt(jwt -> jwt.subject("kc-gateway-security-test")))
				.get().uri("/api/customers/me")
				.exchange()
				.expectStatus().value(status -> assertThat(status).isNotEqualTo(401));
	}

	@Test
	void orderPlacementRoute_rateLimitsBeyondBurstCapacity() {
		// burst-capacity is 10 (application.properties) -- 20 concurrent requests from the same
		// subject (same rate-limit key, RateLimiterConfig) guarantee the bucket empties regardless
		// of how much real-time token replenishment happens between them.
		List<Integer> statuses = IntStream.range(0, 20)
				.parallel()
				.mapToObj(i -> webTestClient.mutateWith(mockJwt().jwt(jwt -> jwt.subject("kc-rate-limit-test")))
						.post().uri("/api/orders/me")
						.contentType(MediaType.APPLICATION_JSON)
						.bodyValue("{}")
						.exchange()
						.returnResult(String.class)
						.getStatus()
						.value())
				.collect(Collectors.toList());

		assertThat(statuses).contains(429);
	}

}
