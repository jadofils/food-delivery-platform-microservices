package food_delivery.Platform.orderservice.dto;

import java.math.BigDecimal;

import food_delivery.Platform.orderservice.entity.OrderItem;

public record OrderItemResponse(Long menuItemId, String name, BigDecimal unitPrice, int quantity) {

	public static OrderItemResponse from(OrderItem item) {
		return new OrderItemResponse(item.getMenuItemId(), item.getName(), item.getUnitPrice(), item.getQuantity());
	}

}
