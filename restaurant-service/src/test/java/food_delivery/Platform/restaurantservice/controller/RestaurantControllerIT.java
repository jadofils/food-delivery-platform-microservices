package food_delivery.Platform.restaurantservice.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;

import food_delivery.Platform.restaurantservice.AbstractIntegrationTest;

/**
 * Mirrors the seeded demo accounts' actual role sets from docker/keycloak/fdp-realm.json, exactly
 * like {@code customer-service}'s equivalent test.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RestaurantControllerIT extends AbstractIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	/** restaurant-owner@fdp.test's actual role set. */
	private static JwtRequestPostProcessor owner(String sub) {
		return jwt().jwt(builder -> builder.subject(sub).claim("email", sub + "@fdp.test"))
				.authorities(new SimpleGrantedAuthority("restaurant:menu:write"),
						new SimpleGrantedAuthority("restaurant:menu:read"),
						new SimpleGrantedAuthority("order:read"));
	}

	/** customer@fdp.test's actual role set -- can browse, cannot manage a restaurant. */
	private static JwtRequestPostProcessor customer(String sub) {
		return jwt().jwt(builder -> builder.subject(sub).claim("email", sub + "@fdp.test"))
				.authorities(new SimpleGrantedAuthority("order:create"), new SimpleGrantedAuthority("order:read"),
						new SimpleGrantedAuthority("restaurant:menu:read"));
	}

	/** delivery-agent@fdp.test's actual role set -- lacks restaurant:menu:read entirely. */
	private static JwtRequestPostProcessor deliveryAgent(String sub) {
		return jwt().jwt(builder -> builder.subject(sub).claim("email", sub + "@fdp.test"))
				.authorities(new SimpleGrantedAuthority("delivery:status:update"),
						new SimpleGrantedAuthority("delivery:read"), new SimpleGrantedAuthority("order:read"));
	}

	private static final String REGISTRATION_JSON = """
			{"name":"Kigali Grill","description":"Grilled specialties","cuisineType":"Rwandan",
			 "street":"1 Downtown Ave","city":"Kigali","state":"Kigali City","postalCode":"00000",
			 "country":"Rwanda"}""";

	@Test
	void unauthenticatedRequest_isRejectedWith401() throws Exception {
		mockMvc.perform(get("/api/restaurants/me"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
	}

	@Test
	void register_thenGetOwnProfile() throws Exception {
		String sub = "kc-owner-register-1";

		mockMvc.perform(post("/api/restaurants/me")
						.with(owner(sub))
						.contentType(MediaType.APPLICATION_JSON)
						.content(REGISTRATION_JSON))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.name").value("Kigali Grill"))
				.andExpect(jsonPath("$.isOpen").value(true));

		mockMvc.perform(get("/api/restaurants/me").with(owner(sub)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.cuisineType").value("Rwandan"));
	}

	@Test
	void register_secondTime_returnsConflict() throws Exception {
		String sub = "kc-owner-register-2";
		mockMvc.perform(post("/api/restaurants/me")
						.with(owner(sub))
						.contentType(MediaType.APPLICATION_JSON)
						.content(REGISTRATION_JSON))
				.andExpect(status().isCreated());

		mockMvc.perform(post("/api/restaurants/me")
						.with(owner(sub))
						.contentType(MediaType.APPLICATION_JSON)
						.content(REGISTRATION_JSON))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("CONFLICT"));
	}

	@Test
	void register_missingName_returnsValidationError() throws Exception {
		mockMvc.perform(post("/api/restaurants/me")
						.with(owner("kc-owner-invalid"))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"","cuisineType":"Rwandan","street":"1 Ave","city":"Kigali",
								 "state":"Kigali City","postalCode":"00000","country":"Rwanda"}"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.errors[0].field").value("name"));
	}

	@Test
	void customer_cannotRegisterARestaurant() throws Exception {
		mockMvc.perform(post("/api/restaurants/me")
						.with(customer("kc-customer-cannot-register"))
						.contentType(MediaType.APPLICATION_JSON)
						.content(REGISTRATION_JSON))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error").value("FORBIDDEN"));
	}

	@Test
	void getOwnProfile_beforeRegistering_returnsNotFound() throws Exception {
		mockMvc.perform(get("/api/restaurants/me").with(owner("kc-owner-never-registered")))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("RESOURCE_NOT_FOUND"));
	}

	@Test
	void updateOwnProfile_changesFieldsAndOpenStatus() throws Exception {
		String sub = "kc-owner-update-1";
		mockMvc.perform(post("/api/restaurants/me")
						.with(owner(sub))
						.contentType(MediaType.APPLICATION_JSON)
						.content(REGISTRATION_JSON))
				.andExpect(status().isCreated());

		mockMvc.perform(put("/api/restaurants/me")
						.with(owner(sub))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"Kigali Grill 2","description":"Now with more grill",
								 "cuisineType":"Rwandan","street":"1 Ave","city":"Kigali",
								 "state":"Kigali City","postalCode":"00000","country":"Rwanda",
								 "isOpen":false}"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Kigali Grill 2"))
				.andExpect(jsonPath("$.isOpen").value(false));
	}

	@Test
	void customerCanBrowse_butDeliveryAgentCannot() throws Exception {
		String sub = "kc-owner-browse-target";
		String createdId = registerAndReturnId(sub);

		mockMvc.perform(get("/api/restaurants/" + createdId).with(customer("kc-customer-browsing")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(Long.valueOf(createdId)));

		mockMvc.perform(get("/api/restaurants").with(customer("kc-customer-listing")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content").isArray());

		mockMvc.perform(get("/api/restaurants/" + createdId).with(deliveryAgent("kc-agent-no-browse")))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error").value("FORBIDDEN"));
	}

	/**
	 * A genuine cache HIT, not just a population check — the first call is guaranteed to miss (a
	 * freshly-registered restaurant can't already be cached), so the second call is the one that
	 * actually exercises Redis's serialize-then-deserialize round-trip for
	 * {@code RestaurantResponse}. This is deliberately not just "does the endpoint still return
	 * 200" — a real bug here surfaced only on a genuine repeat call (see CacheConfig's own class
	 * comment): the first attempt at making this endpoint cacheable embedded no type hint at all,
	 * so a hit deserialized to a generic {@code LinkedHashMap} instead of {@code RestaurantResponse}
	 * and threw a {@code ClassCastException} — invisible to any test that only ever calls an
	 * endpoint once.
	 */
	@Test
	void getById_isCachedAndSurvivesARepeatCall() throws Exception {
		String createdId = registerAndReturnId("kc-owner-cache-hit");

		mockMvc.perform(get("/api/restaurants/" + createdId).with(customer("kc-customer-cache-1")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(Long.valueOf(createdId)))
				.andExpect(jsonPath("$.name").value("Kigali Grill"));

		// Same id, second call -- this one is the cache hit.
		mockMvc.perform(get("/api/restaurants/" + createdId).with(customer("kc-customer-cache-2")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(Long.valueOf(createdId)))
				.andExpect(jsonPath("$.name").value("Kigali Grill"));
	}

	private String registerAndReturnId(String sub) throws Exception {
		String body = mockMvc.perform(post("/api/restaurants/me")
						.with(owner(sub))
						.contentType(MediaType.APPLICATION_JSON)
						.content(REGISTRATION_JSON))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
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
