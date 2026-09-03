package food_delivery.Platform.identityservice;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.jayway.jsonpath.JsonPath;

/**
 * Exercises the real stack — controllers, services, Spring Data repositories, and the Flyway
 * migrations — against an actual Postgres container. Per docs/RULES.md §9, this is the only
 * acceptable way to test anything that touches Postgres; no H2 stand-in. {@code @ServiceConnection}
 * wires the container's JDBC URL/credentials into the Spring context automatically — no manual
 * {@code @DynamicPropertySource}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class IdentityServiceIntegrationTest {

	@Container
	@ServiceConnection
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

	@Autowired
	private MockMvc mockMvc;

	@Test
	void registerThenLogin_roundTripsThroughRealPostgresAndFlywayMigrations() throws Exception {
		String email = "integration-test@fdp.test";
		String body = """
				{"email": "%s", "password": "password123"}""".formatted(email);

		mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.email").value(email))
				.andExpect(jsonPath("$.roles").isEmpty())
				.andExpect(jsonPath("$.enabled").value(true));

		mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value(email));
	}

	@Test
	void register_rejectsADuplicateEmailWithConflict() throws Exception {
		String body = """
				{"email": "duplicate@fdp.test", "password": "password123"}""";

		mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated());
		mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("CONFLICT"));
	}

	@Test
	void login_rejectsAWrongPasswordWithUnauthorized() throws Exception {
		mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email": "wrongpass@fdp.test", "password": "password123"}"""))
				.andExpect(status().isCreated());

		mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email": "wrongpass@fdp.test", "password": "totallywrong"}"""))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
	}

	@Test
	void register_rejectsAShortPasswordWithFieldLevelValidationErrors() throws Exception {
		mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email": "shortpass@fdp.test", "password": "abc"}"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.errors[0].field").value("password"));
	}

	@Test
	void roleAndPermissionLifecycle_worksEndToEndAgainstTheSeededBaseline() throws Exception {
		mockMvc.perform(post("/api/v1/permissions").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name": "test:action", "description": "a test permission"}"""))
				.andExpect(status().isCreated());

		mockMvc.perform(post("/api/v1/roles").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name": "TEST_ROLE", "description": "a test role"}"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.permissions").isEmpty());

		mockMvc.perform(post("/api/v1/roles/TEST_ROLE/permissions/test:action"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.permissions[0]").value("test:action"));

		mockMvc.perform(get("/api/v1/roles/TEST_ROLE"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.permissions[0]").value("test:action"));

		// The seed migration (V2) already created ADMIN with every permission.
		mockMvc.perform(get("/api/v1/roles/ADMIN"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.permissions", hasItem("order:create")));
	}

	@Test
	void assignAndRemoveRole_reflectsImmediatelyOnTheUser() throws Exception {
		String body = """
				{"email": "role-assignment@fdp.test", "password": "password123"}""";
		String userJson = mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content(body))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		String userId = JsonPath.read(userJson, "$.id");

		mockMvc.perform(post("/api/v1/users/" + userId + "/roles/CUSTOMER"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.roles[0]").value("CUSTOMER"))
				.andExpect(jsonPath("$.permissions", hasItem("order:create")));

		mockMvc.perform(delete("/api/v1/users/" + userId + "/roles/CUSTOMER"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.roles").isEmpty());
	}

}
