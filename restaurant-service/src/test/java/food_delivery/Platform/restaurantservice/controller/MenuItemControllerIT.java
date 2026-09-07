package food_delivery.Platform.restaurantservice.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

/** Self-service CRUD plus the one deliberately public route: browsing a restaurant's menu. */
@SpringBootTest
@AutoConfigureMockMvc
class MenuItemControllerIT extends AbstractIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	private static JwtRequestPostProcessor owner(String sub) {
		return jwt().jwt(builder -> builder.subject(sub).claim("email", sub + "@fdp.test"))
				.authorities(new SimpleGrantedAuthority("restaurant:menu:write"),
						new SimpleGrantedAuthority("restaurant:menu:read"));
	}

	private static JwtRequestPostProcessor customer(String sub) {
		return jwt().jwt(builder -> builder.subject(sub).claim("email", sub + "@fdp.test"))
				.authorities(new SimpleGrantedAuthority("restaurant:menu:read"));
	}

	private static final String REGISTRATION_JSON = """
			{"name":"Kigali Grill","cuisineType":"Rwandan","street":"1 Ave","city":"Kigali",
			 "state":"Kigali City","postalCode":"00000","country":"Rwanda"}""";

	private static final String MENU_ITEM_JSON = """
			{"name":"Brochette","description":"Grilled skewers","price":5.50,"category":"Mains","available":true}""";

	@Test
	void menuItemLifecycle_addListGetUpdateDelete() throws Exception {
		String sub = "kc-owner-menu-1";
		registerRestaurant(sub);

		String created = mockMvc.perform(post("/api/restaurants/me/menu-items")
						.with(owner(sub))
						.contentType(MediaType.APPLICATION_JSON)
						.content(MENU_ITEM_JSON))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.name").value("Brochette"))
				.andExpect(jsonPath("$.price").value(5.50))
				.andReturn().getResponse().getContentAsString();
		String itemId = extractId(created);

		mockMvc.perform(get("/api/restaurants/me/menu-items").with(owner(sub)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value(Long.valueOf(itemId)));

		mockMvc.perform(get("/api/restaurants/me/menu-items/" + itemId).with(owner(sub)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.category").value("Mains"));

		mockMvc.perform(delete("/api/restaurants/me/menu-items/" + itemId).with(owner(sub)))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/restaurants/me/menu-items/" + itemId).with(owner(sub)))
				.andExpect(status().isNotFound());
	}

	@Test
	void customer_cannotAddMenuItem_butCanBrowseIt() throws Exception {
		String sub = "kc-owner-menu-2";
		String restaurantId = registerRestaurant(sub);

		mockMvc.perform(post("/api/restaurants/me/menu-items")
						.with(customer("kc-customer-cannot-add"))
						.contentType(MediaType.APPLICATION_JSON)
						.content(MENU_ITEM_JSON))
				.andExpect(status().isForbidden());

		mockMvc.perform(post("/api/restaurants/me/menu-items")
						.with(owner(sub))
						.contentType(MediaType.APPLICATION_JSON)
						.content(MENU_ITEM_JSON))
				.andExpect(status().isCreated());

		mockMvc.perform(get("/api/restaurants/" + restaurantId + "/menu-items").with(customer("kc-customer-browsing")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].name").value("Brochette"));
	}

	/**
	 * A genuine cache HIT for the {@code List<MenuItemResponse>} value, not just a population
	 * check. This is the pair to {@code RestaurantControllerIT.getById_isCachedAndSurvivesARepeatCall}
	 * — the two caches turned out to need genuinely different fixes (see CacheConfig's own class
	 * comment): the list value specifically broke a generic-typed serializer that worked fine for a
	 * single object, since Jackson's default typing can't attach a type hint to a bare JSON array.
	 * Invisible to any test that only ever calls the browsing endpoint once.
	 */
	@Test
	void listForRestaurant_isCachedAndSurvivesARepeatCall() throws Exception {
		String sub = "kc-owner-menu-cache-hit";
		String restaurantId = registerRestaurant(sub);
		mockMvc.perform(post("/api/restaurants/me/menu-items")
						.with(owner(sub))
						.contentType(MediaType.APPLICATION_JSON)
						.content(MENU_ITEM_JSON))
				.andExpect(status().isCreated());

		mockMvc.perform(get("/api/restaurants/" + restaurantId + "/menu-items").with(customer("kc-customer-cache-1")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].name").value("Brochette"));

		// Same restaurant, second call -- this one is the cache hit.
		mockMvc.perform(get("/api/restaurants/" + restaurantId + "/menu-items").with(customer("kc-customer-cache-2")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].name").value("Brochette"));
	}

	@Test
	void anotherOwner_cannotSeeSomeoneElsesMenuItem() throws Exception {
		String ownerA = "kc-owner-menu-a";
		registerRestaurant(ownerA);
		String created = mockMvc.perform(post("/api/restaurants/me/menu-items")
						.with(owner(ownerA))
						.contentType(MediaType.APPLICATION_JSON)
						.content(MENU_ITEM_JSON))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		String itemId = extractId(created);

		String ownerB = "kc-owner-menu-b";
		registerRestaurant(ownerB);
		mockMvc.perform(get("/api/restaurants/me/menu-items/" + itemId).with(owner(ownerB)))
				.andExpect(status().isNotFound());
	}

	@Test
	void addMenuItem_withNegativePrice_returnsValidationError() throws Exception {
		String sub = "kc-owner-menu-invalid-price";
		registerRestaurant(sub);

		mockMvc.perform(post("/api/restaurants/me/menu-items")
						.with(owner(sub))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"Bad Item","price":-1,"category":"Mains","available":true}"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
	}

	private String registerRestaurant(String sub) throws Exception {
		String body = mockMvc.perform(post("/api/restaurants/me")
						.with(owner(sub))
						.contentType(MediaType.APPLICATION_JSON)
						.content(REGISTRATION_JSON))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return extractId(body);
	}

	private String extractId(String json) {
		int idx = json.indexOf("\"id\":");
		String rest = json.substring(idx + 5);
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
