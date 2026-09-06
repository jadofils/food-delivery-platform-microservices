-- name/unit_price are snapshots taken at order-placement time, never updated afterward
-- (see OrderItem's own class comment) -- that's why every column but the surrogate key is
-- NOT NULL and immutable at the application layer.
CREATE TABLE order_items (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id     BIGINT        NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    menu_item_id BIGINT        NOT NULL,
    name         VARCHAR(150)  NOT NULL,
    unit_price   NUMERIC(10,2) NOT NULL,
    quantity     INTEGER       NOT NULL
);

CREATE INDEX ix_order_items_order_id ON order_items (order_id);
