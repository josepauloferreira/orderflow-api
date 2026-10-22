-- Flyway owns optimistic-lock persistence; JPA only maps and validates the column.
-- Existing products start at version zero, so previously persisted rows remain valid.
ALTER TABLE products ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE products ADD CONSTRAINT ck_products_version_nonnegative CHECK (version >= 0);
