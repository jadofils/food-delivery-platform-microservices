-- Owned exclusively by restaurant-service (RULES.md §5) — no other service's migration ever
-- touches this table, and no other service connects to restaurant_db.
CREATE TABLE restaurants (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    owner_keycloak_id VARCHAR(64)  NOT NULL,
    name              VARCHAR(150) NOT NULL,
    description       VARCHAR(1000),
    cuisine_type      VARCHAR(50)  NOT NULL,
    street            VARCHAR(200) NOT NULL,
    city              VARCHAR(100) NOT NULL,
    state             VARCHAR(100) NOT NULL,
    postal_code       VARCHAR(20)  NOT NULL,
    country           VARCHAR(100) NOT NULL,
    is_open           BOOLEAN      NOT NULL DEFAULT true,
    version           BIGINT       NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- One FDP restaurant profile per Keycloak owner identity -- this is what makes
-- POST /api/restaurants/me idempotent to detect (a second call is 409 Conflict).
CREATE UNIQUE INDEX uk_restaurants_owner_keycloak_id ON restaurants (owner_keycloak_id);
