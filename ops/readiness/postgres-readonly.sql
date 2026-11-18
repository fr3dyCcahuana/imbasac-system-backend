BEGIN READ ONLY;
SET LOCAL statement_timeout = '5s';
SET LOCAL lock_timeout = '1s';
SELECT json_build_object(
  'server_version', current_setting('server_version'),
  'in_recovery', pg_is_in_recovery(),
  'fsync', current_setting('fsync'),
  'full_page_writes', current_setting('full_page_writes'),
  'wal_level', current_setting('wal_level'),
  'synchronous_commit', current_setting('synchronous_commit'),
  'synchronous_standby_names', current_setting('synchronous_standby_names'),
  'archive_mode', current_setting('archive_mode'),
  'data_checksums', current_setting('data_checksums'),
  'replicas', COALESCE((SELECT json_agg(json_build_object(
      'state', state, 'sync_state', sync_state,
      'has_flush_lsn', flush_lsn IS NOT NULL)) FROM pg_stat_replication), '[]'::json),
  'role_database_overrides', COALESCE((SELECT json_agg(json_build_object(
      'role_oid', setrole, 'database_oid', setdatabase, 'setting', setting))
      FROM pg_db_role_setting CROSS JOIN LATERAL unnest(setconfig) AS setting
      WHERE split_part(setting, '=', 1) IN ('synchronous_commit', 'default_transaction_read_only')), '[]'::json),
  'scope', 'Snapshot of the diagnostic session, not proof of application transaction settings'
);
COMMIT;
