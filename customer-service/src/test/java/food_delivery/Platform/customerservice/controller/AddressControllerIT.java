package food_delivery.Platform.customerservice.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

/** Address CRUD is entirely self-service — see {@link food_delivery.Platform.customerservice.service.AddressService}. */
@SpringBootTest
@AutoConfigureMockMvc
class AddressControllerIT extends AbstractIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	private static JwtRequestPostProcessor customer(String sub) {
		return jwt().jwt(builder -> builder
				.subject(sub)
				.claim("email", sub + "@fdp.test")
				.claim("given_name", "Demo")
				.claim("family_name", "Customer"))
				.authorities(new SimpleGrantedAuthority("order:create"));
	}

	private static final String ADDRESS_JSON = """
			{"label":"Home","street":"12 Kigali Ave","city":"Kigali","state":"Kigali City",
			 "postalCode":"00000","country":"Rwanda","isDefault":true}""";

	@Test
	void addressLifecycle_addListGetDelete() throws Exception {
		String sub = "kc-user-addr-1";
		registerCustomer(sub);

		String created = mockMvc.perform(post("/api/customers/me/addresses")
						.with(customer(sub))
						.contentType(MediaType.APPLICATION_JSON)
						.content(ADDRESS_JSON))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.label").value("Home"))
				.andExpect(jsonPath("$.isDefault").value(true))
				.andReturn().getResponse().getContentAsString();
		String addressId = extractId(created);

		mockMvc.perform(get("/api/customers/me/addresses").with(customer(sub)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value(Long.valueOf(addressId)));

		mockMvc.perform(get("/api/customers/me/addresses/" + addressId).with(customer(sub)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.city").value("Kigali"));

		mockMvc.perform(delete("/api/customers/me/addresses/" + addressId).with(customer(sub)))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/customers/me/addresses/" + addressId).with(customer(sub)))
				.andExpect(status().isNotFound());
	}

	@Test
	void anotherCustomer_cannotSeeSomeoneElsesAddress() throws Exception {
		String owner = "kc-user-addr-owner";
		registerCustomer(owner);
		String created = mockMvc.perform(post("/api/customers/me/addresses")
						.with(customer(owner))
						.contentType(MediaType.APPLICATION_JSON)
						.content(ADDRESS_JSON))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		String addressId = extractId(created);

		String intruder = "kc-user-addr-intruder";
		registerCustomer(intruder);
		mockMvc.perform(get("/api/customers/me/addresses/" + addressId).with(customer(intruder)))
				.andExpect(status().isNotFound());
	}

	private void registerCustomer(String sub) throws Exception {
		mockMvc.perform(post("/api/customers/me")
						.with(customer(sub))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"phoneNumber\":\"+15551234567\"}"))
				.andExpect(status().isCreated());
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
