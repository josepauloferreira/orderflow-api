-- Snapshot active transaction and lock waits. Empty results are normal if
-- contention is too brief to overlap the observation.
SELECT now() AS sampled_at, pid, state, wait_event_type, wait_event,
       array_length(pg_blocking_pids(pid), 1) AS blocking_sessions,
       left(query, 120) AS query_prefix
FROM pg_stat_activity
WHERE datname = current_database()
  AND pid <> pg_backend_pid()
  AND (wait_event_type = 'Lock' OR cardinality(pg_blocking_pids(pid)) > 0)
ORDER BY pid;

SELECT locktype, mode, granted, count(*) AS sessions
FROM pg_locks
WHERE pid IN (SELECT pid FROM pg_stat_activity WHERE datname = current_database())
GROUP BY locktype, mode, granted
ORDER BY locktype, mode, granted;
