-- Flyway is the only owner of production DDL.
-- SKU is stored in canonical uppercase form, enforced by a database check.
CREATE TABLE products (
    id UUID PRIMARY KEY,
    sku VARCHAR(40) NOT NULL,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(1000),
    price NUMERIC(12, 2) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_products_sku UNIQUE (sku),
    CONSTRAINT ck_products_sku_format CHECK (sku ~ '^[A-Z0-9][A-Z0-9_-]{2,39}$'),
    CONSTRAINT ck_products_name_not_blank CHECK (length(btrim(name)) > 0),
    CONSTRAINT ck_products_price_positive CHECK (price > 0)
);
