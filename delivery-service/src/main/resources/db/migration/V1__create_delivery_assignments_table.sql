-- Owned exclusively by delivery-service (RULES.md §5) -- no other service's migration ever touches
-- this table, and no other service connects to delivery_db. restaurant_id/customer_keycloak_id/
-- delivery_address_id are plain reference columns, never foreign keys into another service's
-- database (RULES.md §5) -- copied straight off OrderPlacedEvent, never queried from order_db/
-- customer_db/restaurant_db directly.
--
-- order_id is UNIQUE: one delivery assignment per order, and the real idempotency guard against a
-- redelivered OrderPlacedEvent (see OrderEventListener's own javadoc) -- not just the consumer's
-- application-level existsByOrderId pre-check.
CREATE TABLE delivery_assignments (
    id                        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id                  BIGINT       NOT NULL UNIQUE,
    restaurant_id             BIGINT       NOT NULL,
    customer_keycloak_id      VARCHAR(64)  NOT NULL,
    delivery_address_id       BIGINT       NOT NULL,
    assigned_agent_keycloak_id VARCHAR(64),
    status                    VARCHAR(20)  NOT NULL,
    version                   BIGINT       NOT NULL DEFAULT 0,
    created_at                TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- "My own claimed deliveries" and "unclaimed deliveries" each filter on one of these.
CREATE INDEX ix_delivery_assignments_agent ON delivery_assignments (assigned_agent_keycloak_id);
CREATE INDEX ix_delivery_assignments_status ON delivery_assignments (status);
