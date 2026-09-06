-- Owned exclusively by order-service (RULES.md §5) — no other service's migration ever touches
-- this table, and no other service connects to order_db. customer_id/restaurant_id/
-- delivery_address_id are plain reference columns, never foreign keys into another service's
-- database (RULES.md §5) — order-service validated them via OpenFeign before this row existed.
CREATE TABLE orders (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_keycloak_id VARCHAR(64)  NOT NULL,
    customer_id          BIGINT       NOT NULL,
    restaurant_id        BIGINT       NOT NULL,
    delivery_address_id  BIGINT       NOT NULL,
    status               VARCHAR(20)  NOT NULL,
    total_amount         NUMERIC(10,2) NOT NULL,
    version              BIGINT       NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Every "my own orders" query filters on this.
CREATE INDEX ix_orders_customer_keycloak_id ON orders (customer_keycloak_id);
