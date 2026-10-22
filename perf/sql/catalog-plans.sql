-- Inspect SQL shape, not a hard-coded index-use assertion. Small datasets
-- legitimately select sequential scans. Capture BUFFERS and execution time
-- on a representative number and distribution of product rows.
\timing on
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT id, sku, name, description, price, active, version, created_at, updated_at
FROM products
WHERE active = TRUE
ORDER BY created_at DESC, id DESC
LIMIT 20 OFFSET 0;

EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT count(*) FROM products WHERE active = TRUE;

EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT id, sku, name, description, price, active, version, created_at, updated_at
FROM products
ORDER BY created_at DESC, id DESC
LIMIT 20 OFFSET 0;

SELECT
  relname,
  seq_scan,
  idx_scan,
  n_live_tup,
  n_dead_tup
FROM pg_stat_user_tables
WHERE relname IN ('products', 'inventory', 'customer_orders', 'order_items')
ORDER BY relname;
