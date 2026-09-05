package food_delivery.Platform.customerservice.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;

import food_delivery.Platform.customerservice.AbstractIntegrationTest;

/**
 * Exercises the exact flow a Postman collection also exercises: register → get own profile
 * (masked) → update → admin-only access denied/granted. Every request here uses
 * {@code jwt()} to inject an already-built {@code Authentication} — see
 * {@link AbstractIntegrationTest} for why that's safe without a real Keycloak running.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CustomerControllerIT extends AbstractIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	/** Mirrors the seeded {@code customer@fdp.test} demo account's actual role set (fdp-realm.json). */
	private static JwtRequestPostProcessor customer(String sub) {
		return jwt().jwt(builder -> builder
				.subject(sub)
				.claim("email", sub + "@fdp.test")
				.claim("given_name", "Demo")
				.claim("family_name", "Customer"))
				.authorities(new SimpleGrantedAuthority("order:create"), new SimpleGrantedAuthority("order:read"));
	}

	/** Mirrors the seeded {@code admin@fdp.test} demo account's actual role set (fdp-realm.json). */
	private static JwtRequestPostProcessor admin(String sub) {
		return jwt().jwt(builder -> builder
				.subject(sub)
				.claim("email", "admin@fdp.test")
				.claim("given_name", "Admin")
				.claim("family_name", "User"))
				.authorities(new SimpleGrantedAuthority("user:read"), new SimpleGrantedAuthority("user:manage"));
	}

	@Test
	void unauthenticatedRequest_isRejectedWith401() throws Exception {
		mockMvc.perform(get("/api/customers/me"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
	}

	@Test
	void register_thenGetOwnProfile_masksEmailAndPhone() throws Exception {
		String sub = "kc-user-register-1";

		mockMvc.perform(post("/api/customers/me")
						.with(customer(sub))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"phoneNumber\":\"+15551234567\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").exists())
				// first char + fixed mask + last char (PiiMasking) -- never the raw value.
				.andExpect(jsonPath("$.email").value("k" + "*****" + "t"))
				.andExpect(jsonPath("$.phoneNumber").value("+" + "*****" + "7"))
				.andExpect(jsonPath("$.firstName").value("Demo"));

		mockMvc.perform(get("/api/customers/me").with(customer(sub)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.phoneNumber").value("+" + "*****" + "7"));
	}

	@Test
	void register_secondTime_returnsConflict() throws Exception {
		String sub = "kc-user-register-2";
		mockMvc.perform(post("/api/customers/me")
						.with(customer(sub))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"phoneNumber\":\"+15551234567\"}"))
				.andExpect(status().isCreated());

		mockMvc.perform(post("/api/customers/me")
						.with(customer(sub))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"phoneNumber\":\"+15551234567\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("CONFLICT"));
	}

	@Test
	void register_withInvalidPhoneNumber_returnsValidationError() throws Exception {
		mockMvc.perform(post("/api/customers/me")
						.with(customer("kc-user-invalid-phone"))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"phoneNumber\":\"not-a-phone\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.errors[0].field").value("phoneNumber"));
	}

	@Test
	void getOwnProfile_beforeRegistering_returnsNotFound() throws Exception {
		mockMvc.perform(get("/api/customers/me").with(customer("kc-user-never-registered")))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("RESOURCE_NOT_FOUND"));
	}

	@Test
	void updateOwnProfile_changesPhoneNumber() throws Exception {
		String sub = "kc-user-update-1";
		mockMvc.perform(post("/api/customers/me")
						.with(customer(sub))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"phoneNumber\":\"+15551111111\"}"))
				.andExpect(status().isCreated());

		mockMvc.perform(put("/api/customers/me")
						.with(customer(sub))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"phoneNumber\":\"+15559999999\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.phoneNumber").value("+" + "*****" + "9"));
	}

	@Test
	void plainCustomer_cannotReadAnotherCustomerById_butAdminCan() throws Exception {
		String sub = "kc-user-admin-target";
		String createdId = registerAndReturnId(sub);

		mockMvc.perform(get("/api/customers/" + createdId).with(customer("kc-user-someone-else")))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error").value("FORBIDDEN"));

		mockMvc.perform(get("/api/customers/" + createdId).with(admin("kc-admin-1")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(Long.valueOf(createdId)));
	}

	@Test
	void plainCustomer_cannotListCustomers_butAdminCan() throws Exception {
		mockMvc.perform(get("/api/customers").with(customer("kc-user-list-attempt")))
				.andExpect(status().isForbidden());

		mockMvc.perform(get("/api/customers").with(admin("kc-admin-2")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content").isArray());
	}

	private String registerAndReturnId(String sub) throws Exception {
		String body = mockMvc.perform(post("/api/customers/me")
						.with(customer(sub))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"phoneNumber\":\"+15551234567\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		// Minimal extraction -- no Jackson bean needed for one field in a test helper.
		int idx = body.indexOf("\"id\":");
		String rest = body.substring(idx + 5);
		StringBuilder digits = new StringBuilder();
		for (char c : rest.toCharArray()) {
			if (Character.isDigit(c)) {
				digits.append(c);
			} else if (!digits.isEmpty()) {
				break;
			}
		}
		return digits.toString();
	}

}
