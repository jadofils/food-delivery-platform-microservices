CREATE TABLE menu_items (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    restaurant_id BIGINT        NOT NULL REFERENCES restaurants (id) ON DELETE CASCADE,
    name          VARCHAR(150)  NOT NULL,
    description   VARCHAR(1000),
    price         NUMERIC(10,2) NOT NULL,
    category      VARCHAR(50)   NOT NULL,
    available     BOOLEAN       NOT NULL DEFAULT true,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- The FK column is the one every "menu items for this restaurant" query filters on.
CREATE INDEX ix_menu_items_restaurant_id ON menu_items (restaurant_id);
