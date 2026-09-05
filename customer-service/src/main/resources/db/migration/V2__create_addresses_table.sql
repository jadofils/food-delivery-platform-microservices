CREATE TABLE addresses (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_id  BIGINT       NOT NULL REFERENCES customers (id) ON DELETE CASCADE,
    label        VARCHAR(50)  NOT NULL,
    street       VARCHAR(200) NOT NULL,
    city         VARCHAR(100) NOT NULL,
    state        VARCHAR(100) NOT NULL,
    postal_code  VARCHAR(20)  NOT NULL,
    country      VARCHAR(100) NOT NULL,
    is_default   BOOLEAN      NOT NULL DEFAULT false,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- The FK column is the one every "addresses for this customer" query filters on.
CREATE INDEX ix_addresses_customer_id ON addresses (customer_id);
