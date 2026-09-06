package food_delivery.Platform.orderservice.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A line item on an {@link Order}, snapshotting {@link #name}/{@link #unitPrice} from
 * {@code restaurant-service}'s menu item at the moment the order was placed. Deliberately a
 * snapshot, not a live reference: order history must stay stable even if the restaurant later
 * renames the item or changes its price — re-reading current menu data for a historical order
 * would silently rewrite that order's own record of what the customer actually agreed to pay.
 * {@link #menuItemId} is kept purely as a traceable reference back to the source item.
 */
@Entity
@Table(name = "order_items")
@Getter
@Setter
@NoArgsConstructor
public class OrderItem {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "order_id", nullable = false)
	private Order order;

	@Column(name = "menu_item_id", nullable = false, updatable = false)
	private Long menuItemId;

	@Column(nullable = false, updatable = false)
	private String name;

	@Column(name = "unit_price", nullable = false, updatable = false, precision = 10, scale = 2)
	private BigDecimal unitPrice;

	@Column(nullable = false, updatable = false)
	private int quantity;

	public OrderItem(Long menuItemId, String name, BigDecimal unitPrice, int quantity) {
		this.menuItemId = menuItemId;
		this.name = name;
		this.unitPrice = unitPrice;
		this.quantity = quantity;
	}

	public BigDecimal lineTotal() {
		return unitPrice.multiply(BigDecimal.valueOf(quantity));
	}

}
