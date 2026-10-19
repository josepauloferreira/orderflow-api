-- The previous version has no authenticated buyers. No orders must be
-- present when applying this development-only ownership migration.
-- Production data would need an explicit, audited backfill first.
ALTER TABLE customer_orders ADD COLUMN customer_subject VARCHAR(200);
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM customer_orders WHERE customer_subject IS NULL) THEN
    RAISE EXCEPTION 'Existing orders require customer ownership backfill before migration V5';
  END IF;
END $$;
ALTER TABLE customer_orders ALTER COLUMN customer_subject SET NOT NULL;
CREATE INDEX idx_customer_orders_owner ON customer_orders (customer_subject, created_at DESC);

CREATE TABLE order_idempotency (
  customer_subject VARCHAR(200) NOT NULL,
  idempotency_key VARCHAR(128) NOT NULL,
  request_digest CHAR(64) NOT NULL,
  order_id UUID REFERENCES customer_orders (id) ON DELETE RESTRICT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (customer_subject, idempotency_key),
  CONSTRAINT ck_idempotency_digest CHECK (request_digest ~ '^[0-9a-f]{64}$')
);
CREATE UNIQUE INDEX uk_idempotency_order ON order_idempotency (order_id);
