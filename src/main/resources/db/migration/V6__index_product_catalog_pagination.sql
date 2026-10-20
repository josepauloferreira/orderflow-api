-- Catalog pagination sorts by creation time and UUID, so index those columns.
-- A separate composite index serves the active/inactive filter.
-- Flyway is the authoritative source of database indexes.
CREATE INDEX idx_products_created_id ON products (created_at DESC, id DESC);
CREATE INDEX idx_products_active_created_id
    ON products (active, created_at DESC, id DESC);
