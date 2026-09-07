package food_delivery.Platform.orderservice.controller;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import food_delivery.Platform.common.event.OrderPlacedEvent;
import food_delivery.Platform.orderservice.AbstractIntegrationTest;
import food_delivery.Platform.orderservice.client.CustomerServiceGateway;
import food_delivery.Platform.orderservice.client.DeliveryServiceGateway;
import food_delivery.Platform.orderservice.client.RestaurantServiceGateway;
import food_delivery.Platform.orderservice.client.dto.CustomerProfileResponse;
import food_delivery.Platform.orderservice.client.dto.DeliveryAddressResponse;
import food_delivery.Platform.orderservice.client.dto.MenuItemValidationResponse;
import food_delivery.Platform.orderservice.client.dto.RestaurantValidationResponse;

/**
 * The two Feign-backed gateways are mocked here — this proves order-service's own business logic
 * (validation ordering, price snapshotting, permission checks, event publishing) end to end
 * without needing real running instances of customer-service/restaurant-service in the test JVM.
 * The *real* integration across all three live services is exercised manually against the running
 * stack (see docs/services/order-service.md) and by the checked-in Postman collection — this test
 * class is the fast, deterministic half of RULES.md §9's testing story, not the whole of it.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OrderControllerIT extends AbstractIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private RabbitTemplate rabbitTemplate;

	@MockitoBean
	private CustomerServiceGateway customerServiceGateway;

	@MockitoBean
	private RestaurantServiceGateway restaurantServiceGateway;

	@MockitoBean
	private DeliveryServiceGateway deliveryServiceGateway;

	private static JwtRequestPostProcessor customer(String sub) {
		return jwt().jwt(builder -> builder.subject(sub).claim("email", sub + "@fdp.test"))
				.authorities(new SimpleGrantedAuthority("order:create"), new SimpleGrantedAuthority("order:cancel"),
						new SimpleGrantedAuthority("order:read"));
	}

	/** restaurant-owner@fdp.test's actual role set -- has order:read but not order:create. */
	private static JwtRequestPostProcessor restaurantOwnerOnly(String sub) {
		return jwt().jwt(builder -> builder.subject(sub).claim("email", sub + "@fdp.test"))
				.authorities(new SimpleGrantedAuthority("order:read"));
	}

	private void stubHappyPath() {
		when(customerServiceGateway.getMyProfile()).thenReturn(new CustomerProfileResponse(1L, "Demo", "Customer"));
		when(customerServiceGateway.getMyAddress(anyLong()))
				.thenReturn(new DeliveryAddressResponse(1L, "1 Ave", "Kigali", "Kigali City", "00000", "Rwanda"));
		when(restaurantServiceGateway.getRestaurant(anyLong()))
				.thenReturn(new RestaurantValidationResponse(1L, "Kigali Grill", true));
		when(restaurantServiceGateway.getMenuItems(anyLong())).thenReturn(List.of(
				new MenuItemValidationResponse(10L, "Brochette", new BigDecimal("5.50"), true),
				new MenuItemValidationResponse(11L, "Soda", new BigDecimal("1.50"), false)));
	}

	private static final String PLACE_ORDER_JSON = """
			{"restaurantId":1,"deliveryAddressId":1,"items":[{"menuItemId":10,"quantity":2}]}""";

	@Test
	void unauthenticatedRequest_isRejectedWith401() throws Exception {
		mockMvc.perform(post("/api/orders/me").contentType(MediaType.APPLICATION_JSON).content(PLACE_ORDER_JSON))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void placeOrder_success_snapshotsPriceAndPublishesEvent() throws Exception {
		stubHappyPath();

		// Every other test in this class that places an order also publishes onto this same shared
		// order-events.inspection queue (RabbitConfig's own class comment) without draining it --
		// purge first so a leftover message from another test can never be mistaken for this one's
		// own event, regardless of test execution order.
		rabbitTemplate.execute(channel -> {
			channel.queuePurge("order-events.inspection");
			return null;
		});

		String body = mockMvc.perform(post("/api/orders/me")
						.with(customer("kc-order-1"))
						.contentType(MediaType.APPLICATION_JSON)
						.content(PLACE_ORDER_JSON))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("PLACED"))
				.andExpect(jsonPath("$.totalAmount").value(11.00))
				.andExpect(jsonPath("$.items[0].name").value("Brochette"))
				.andReturn().getResponse().getContentAsString();

		assertThat(body).contains("\"quantity\":2");

		OrderPlacedEvent event = (OrderPlacedEvent) rabbitTemplate.receiveAndConvert("order-events.inspection", 5000);
		assertThat(event).isNotNull();
		assertThat(event.customerKeycloakId()).isEqualTo("kc-order-1");
		assertThat(event.totalAmount()).isEqualByComparingTo("11.00");
	}

	@Test
	void placeOrder_restaurantClosed_returns422() throws Exception {
		when(customerServiceGateway.getMyProfile()).thenReturn(new CustomerProfileResponse(1L, "Demo", "Customer"));
		when(customerServiceGateway.getMyAddress(anyLong()))
				.thenReturn(new DeliveryAddressResponse(1L, "1 Ave", "Kigali", "Kigali City", "00000", "Rwanda"));
		when(restaurantServiceGateway.getRestaurant(anyLong()))
				.thenReturn(new RestaurantValidationResponse(1L, "Kigali Grill", false));

		mockMvc.perform(post("/api/orders/me")
						.with(customer("kc-order-2"))
						.contentType(MediaType.APPLICATION_JSON)
						.content(PLACE_ORDER_JSON))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.error").value("BUSINESS_RULE_VIOLATION"));
	}

	@Test
	void placeOrder_menuItemUnavailable_returns422() throws Exception {
		stubHappyPath();
		mockMvc.perform(post("/api/orders/me")
						.with(customer("kc-order-3"))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"restaurantId":1,"deliveryAddressId":1,"items":[{"menuItemId":11,"quantity":1}]}"""))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.error").value("BUSINESS_RULE_VIOLATION"));
	}

	@Test
	void placeOrder_menuItemNotOnRestaurant_returns404() throws Exception {
		stubHappyPath();
		mockMvc.perform(post("/api/orders/me")
						.with(customer("kc-order-4"))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"restaurantId":1,"deliveryAddressId":1,"items":[{"menuItemId":999,"quantity":1}]}"""))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("RESOURCE_NOT_FOUND"));
	}

	@Test
	void placeOrder_withoutOrderCreatePermission_returns403() throws Exception {
		mockMvc.perform(post("/api/orders/me")
						.with(restaurantOwnerOnly("kc-owner-cannot-order"))
						.contentType(MediaType.APPLICATION_JSON)
						.content(PLACE_ORDER_JSON))
				.andExpect(status().isForbidden());
	}

	@Test
	void placeOrder_invalidBody_returns400() throws Exception {
		mockMvc.perform(post("/api/orders/me")
						.with(customer("kc-order-invalid"))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"restaurantId\":null,\"deliveryAddressId\":1,\"items\":[]}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
	}

	@Test
	void cancelOrder_thenCancelAgain_returnsConflict() throws Exception {
		stubHappyPath();
		String sub = "kc-order-cancel-1";
		String body = mockMvc.perform(post("/api/orders/me")
						.with(customer(sub))
						.contentType(MediaType.APPLICATION_JSON)
						.content(PLACE_ORDER_JSON))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		String orderId = extractId(body);

		mockMvc.perform(post("/api/orders/me/" + orderId + "/cancel").with(customer(sub)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CANCELLED"));

		mockMvc.perform(post("/api/orders/me/" + orderId + "/cancel").with(customer(sub)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("CONFLICT"));
	}

	@Test
	void anotherCustomer_cannotSeeSomeoneElsesOrder() throws Exception {
		stubHappyPath();
		String owner = "kc-order-owner";
		String body = mockMvc.perform(post("/api/orders/me")
						.with(customer(owner))
						.contentType(MediaType.APPLICATION_JSON)
						.content(PLACE_ORDER_JSON))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		String orderId = extractId(body);

		mockMvc.perform(get("/api/orders/me/" + orderId).with(customer("kc-order-intruder")))
				.andExpect(status().isNotFound());
	}

	@Test
	void getOwnOrder_includesLiveDeliveryStatusWhenAvailable() throws Exception {
		stubHappyPath();
		String sub = "kc-order-tracking-1";
		String body = mockMvc.perform(post("/api/orders/me")
						.with(customer(sub))
						.contentType(MediaType.APPLICATION_JSON)
						.content(PLACE_ORDER_JSON))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		String orderId = extractId(body);

		when(deliveryServiceGateway.getStatusByOrderId(Long.valueOf(orderId))).thenReturn("PICKED_UP");

		mockMvc.perform(get("/api/orders/me/" + orderId).with(customer(sub)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.deliveryStatus").value("PICKED_UP"));
	}

	@Test
	void getOwnOrder_deliveryStatusIsNull_whenNoAssignmentYetOrDeliveryServiceUnavailable() throws Exception {
		stubHappyPath();
		String sub = "kc-order-tracking-2";
		String body = mockMvc.perform(post("/api/orders/me")
						.with(customer(sub))
						.contentType(MediaType.APPLICATION_JSON)
						.content(PLACE_ORDER_JSON))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		String orderId = extractId(body);

		// deliveryServiceGateway is left unstubbed -- Mockito's default null return is exactly what
		// DeliveryServiceGateway itself returns for "no assignment yet" or "unreachable" (see its
		// own class comment) -- the order itself must still be fully viewable either way.
		mockMvc.perform(get("/api/orders/me/" + orderId).with(customer(sub)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("PLACED"))
				.andExpect(jsonPath("$.deliveryStatus").value(org.hamcrest.Matchers.nullValue()));
	}

	@Test
	void listOwnOrders_returnsPagedResult() throws Exception {
		stubHappyPath();
		String sub = "kc-order-list-1";
		mockMvc.perform(post("/api/orders/me")
						.with(customer(sub))
						.contentType(MediaType.APPLICATION_JSON)
						.content(PLACE_ORDER_JSON))
				.andExpect(status().isCreated());

		mockMvc.perform(get("/api/orders/me").with(customer(sub)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content").isArray());
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
