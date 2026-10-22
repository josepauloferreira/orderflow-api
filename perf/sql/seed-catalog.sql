-- Synthetic, disposable, non-production dataset. DO NOT run against a real
-- or shared database. Called only by the bounded local/CI test harness.
-- Creates reproducible active/inactive distribution and timestamp range.
INSERT INTO products
  (id, sku, name, description, price, active, version, created_at, updated_at)
SELECT gen_random_uuid(),
       'BMARK-' || lpad(n::text, 6, '0'),
       'Synthetic product ' || n,
       'Stage8 query-plan fixture',
       (100 + n % 100)::numeric(12, 2),
       (n % 10 <> 0),
       0,
       now() - n * interval '1 minute',
       now() - n * interval '1 minute'
FROM generate_series(1, 5000) AS n
ON CONFLICT (sku) DO NOTHING;

ANALYZE products;
SELECT count(*) AS synthetic_rows FROM products WHERE sku LIKE 'BMARK-%';
