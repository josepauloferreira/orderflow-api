-- Every stock mutation must generate exactly one movement in the same transaction.
-- A unique order/product/kind tuple guards duplicate order effects.
CREATE TABLE stock_movements (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL REFERENCES products (id) ON DELETE RESTRICT,
    order_id UUID REFERENCES customer_orders (id) ON DELETE RESTRICT,
    kind VARCHAR(32) NOT NULL,
    delta INTEGER NOT NULL,
    note VARCHAR(200),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_stock_movements_delta_nonzero CHECK (delta <> 0),
    CONSTRAINT ck_stock_movements_kind CHECK (
      kind IN ('MANUAL_ADJUSTMENT', 'ORDER_CONFIRMED', 'ORDER_CANCELLED')
    ),
    CONSTRAINT ck_stock_movements_origin CHECK (
      (kind = 'MANUAL_ADJUSTMENT' AND order_id IS NULL AND note IS NOT NULL)
      OR (kind IN ('ORDER_CONFIRMED', 'ORDER_CANCELLED') AND order_id IS NOT NULL)
    ),
    CONSTRAINT uk_stock_movements_order_effect UNIQUE (order_id, product_id, kind)
);

CREATE INDEX idx_stock_movements_product_time
    ON stock_movements (product_id, created_at DESC);
