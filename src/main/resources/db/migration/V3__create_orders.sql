-- Orders have only CONFIRMED and CANCELLED states until payment processing exists.
-- Prices and product identity are snapshotted to preserve order history.
CREATE TABLE customer_orders (
    id UUID PRIMARY KEY,
    status VARCHAR(20) NOT NULL,
    total_amount NUMERIC(14, 2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_orders_status CHECK (status IN ('CONFIRMED', 'CANCELLED')),
    CONSTRAINT ck_orders_total_positive CHECK (total_amount > 0)
);

CREATE TABLE order_items (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES customer_orders (id) ON DELETE RESTRICT,
    product_id UUID NOT NULL REFERENCES products (id) ON DELETE RESTRICT,
    sku_snapshot VARCHAR(40) NOT NULL,
    name_snapshot VARCHAR(120) NOT NULL,
    unit_price NUMERIC(12, 2) NOT NULL,
    quantity INTEGER NOT NULL,
    line_total NUMERIC(14, 2) NOT NULL,
    CONSTRAINT uk_order_items_order_product UNIQUE (order_id, product_id),
    CONSTRAINT ck_order_items_quantity_positive CHECK (quantity > 0),
    CONSTRAINT ck_order_items_unit_price_positive CHECK (unit_price > 0),
    CONSTRAINT ck_order_items_line_total_positive CHECK (line_total > 0)
);

CREATE INDEX idx_order_items_product ON order_items (product_id);
CREATE INDEX idx_orders_created_at ON customer_orders (created_at DESC, id DESC);
