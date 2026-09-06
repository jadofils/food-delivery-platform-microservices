package food_delivery.Platform.orderservice.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import food_delivery.Platform.common.error.BusinessRuleViolationException;
import food_delivery.Platform.common.error.ConflictException;
import food_delivery.Platform.common.error.ResourceNotFoundException;
import food_delivery.Platform.common.security.jwt.JwtClaims;
import food_delivery.Platform.orderservice.client.CustomerServiceGateway;
import food_delivery.Platform.orderservice.client.RestaurantServiceGateway;
import food_delivery.Platform.orderservice.client.dto.MenuItemValidationResponse;
import food_delivery.Platform.orderservice.dto.OrderItemRequest;
import food_delivery.Platform.orderservice.dto.PlaceOrderRequest;
import food_delivery.Platform.orderservice.entity.Order;
import food_delivery.Platform.orderservice.entity.OrderItem;
import food_delivery.Platform.orderservice.entity.OrderStatus;
import food_delivery.Platform.orderservice.messaging.OrderEventPublisher;
import food_delivery.Platform.orderservice.repository.OrderRepository;

/**
 * Every placement validates against real, live {@code customer-service}/{@code restaurant-service}
 * data via OpenFeign (RULES.md §6) before a single row is written — never trusts a client-supplied
 * price or a client-supplied claim that an address/restaurant/menu item exists.
 */
@Service
public class OrderService {

	private final OrderRepository orderRepository;
	private final CustomerServiceGateway customerServiceGateway;
	private final RestaurantServiceGateway restaurantServiceGateway;
	private final OrderEventPublisher eventPublisher;

	public OrderService(OrderRepository orderRepository, CustomerServiceGateway customerServiceGateway,
			RestaurantServiceGateway restaurantServiceGateway, OrderEventPublisher eventPublisher) {
		this.orderRepository = orderRepository;
		this.customerServiceGateway = customerServiceGateway;
		this.restaurantServiceGateway = restaurantServiceGateway;
		this.eventPublisher = eventPublisher;
	}

	@Transactional
	public Order placeOrder(Jwt jwt, PlaceOrderRequest request) {
		String customerKeycloakId = JwtClaims.subject(jwt);

		// 1. Sync validation against customer-service — customer profile and delivery address
		// must both be real, and the address must belong to this exact caller (its own /me route
		// enforces that ownership check, not this service).
		Long customerId = customerServiceGateway.getMyProfile().id();
		customerServiceGateway.getMyAddress(request.deliveryAddressId());

		// 2. Sync validation against restaurant-service — restaurant must exist and be open.
		var restaurant = restaurantServiceGateway.getRestaurant(request.restaurantId());
		if (!restaurant.isOpen()) {
			throw new BusinessRuleViolationException("Restaurant " + restaurant.name() + " is currently closed.");
		}

		// 3. Every requested item must exist on that restaurant's menu and be available; price is
		// always the restaurant's current price, never a client-supplied one.
		List<MenuItemValidationResponse> menu = restaurantServiceGateway.getMenuItems(request.restaurantId());
		Map<Long, MenuItemValidationResponse> menuById = menu.stream()
				.collect(java.util.stream.Collectors.toMap(MenuItemValidationResponse::id, Function.identity()));

		Order order = new Order(customerKeycloakId, customerId, request.restaurantId(),
				request.deliveryAddressId(), BigDecimal.ZERO);
		BigDecimal total = BigDecimal.ZERO;
		for (OrderItemRequest itemRequest : request.items()) {
			MenuItemValidationResponse menuItem = menuById.get(itemRequest.menuItemId());
			if (menuItem == null) {
				throw new ResourceNotFoundException(
						"No menu item " + itemRequest.menuItemId() + " on restaurant " + request.restaurantId());
			}
			if (!menuItem.available()) {
				throw new BusinessRuleViolationException(menuItem.name() + " is currently unavailable.");
			}
			OrderItem orderItem = new OrderItem(menuItem.id(), menuItem.name(), menuItem.price(),
					itemRequest.quantity());
			order.addItem(orderItem);
			total = total.add(orderItem.lineTotal());
		}
		order.setTotalAmount(total);

		Order saved = orderRepository.save(order);
		eventPublisher.publishOrderPlaced(saved);
		return saved;
	}

	@Transactional(readOnly = true)
	public Page<Order> listOwn(Jwt jwt, Pageable pageable) {
		return orderRepository.findByCustomerKeycloakId(JwtClaims.subject(jwt), pageable);
	}

	@Transactional(readOnly = true)
	public Order getOwn(Jwt jwt, Long orderId) {
		return findOwnOrThrow(jwt, orderId);
	}

	@Transactional
	public Order cancelOwn(Jwt jwt, Long orderId) {
		Order order = findOwnOrThrow(jwt, orderId);
		if (order.getStatus() != OrderStatus.PLACED) {
			throw new ConflictException("Order " + orderId + " is already " + order.getStatus() + ".");
		}
		order.setStatus(OrderStatus.CANCELLED);
		eventPublisher.publishOrderCancelled(order);
		return order;
	}

	private Order findOwnOrThrow(Jwt jwt, Long orderId) {
		return orderRepository.findWithItemsByIdAndCustomerKeycloakId(orderId, JwtClaims.subject(jwt))
				.orElseThrow(() -> new ResourceNotFoundException("No order " + orderId + " on this account."));
	}

}
