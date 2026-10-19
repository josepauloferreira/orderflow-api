-- Inventory rows are initialized on the first positive adjustment.
-- All stock changes must lock the corresponding product row before reading inventory.
CREATE TABLE inventory (
    product_id UUID PRIMARY KEY REFERENCES products (id) ON DELETE RESTRICT,
    available_quantity INTEGER NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_inventory_nonnegative CHECK (available_quantity >= 0)
);
