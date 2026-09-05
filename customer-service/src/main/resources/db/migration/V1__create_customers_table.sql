-- Owned exclusively by customer-service (RULES.md §5) — no other service's migration ever
-- touches this table, and no other service connects to customer_db.
CREATE TABLE customers (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    keycloak_id   VARCHAR(64)  NOT NULL,
    email         VARCHAR(320) NOT NULL,
    phone_number  VARCHAR(32)  NOT NULL,
    first_name    VARCHAR(100),
    last_name     VARCHAR(100),
    version       BIGINT       NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- One FDP profile per Keycloak identity -- this is what makes POST /api/customers/me idempotent
-- to detect (a second call is a 409 Conflict, not a second row).
CREATE UNIQUE INDEX uk_customers_keycloak_id ON customers (keycloak_id);
