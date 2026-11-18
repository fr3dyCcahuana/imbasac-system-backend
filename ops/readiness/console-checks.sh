#!/usr/bin/env bash
# Ubuntu only. Read-only follow-up after PostgreSQL 18/main and pos-backend were identified.
# Run in the Hostinger console. Never print Environment, ExecStart or complete config files.
systemctl show pos-backend.service --property=User,Group,WorkingDirectory,FragmentPath,Restart,NoNewPrivileges,ProtectSystem,PrivateTmp --no-pager
ufw status verbose
ss -lntp '( sport = :8091 or sport = :8093 or sport = :5432 )'
ls -l /etc/nginx/sites-enabled/
runuser -u postgres -- psql -X -w -h /var/run/postgresql -p 5432 -d postgres -P pager=off -v ON_ERROR_STOP=1 <<'SQL'
BEGIN READ ONLY;
SET LOCAL statement_timeout = '5s';
SELECT name, setting FROM pg_settings
WHERE name IN ('server_version','listen_addresses','port','ssl','fsync','full_page_writes','wal_level','synchronous_commit','synchronous_standby_names','archive_mode','data_checksums')
ORDER BY name;
SELECT state, sync_state FROM pg_stat_replication;
SELECT line_number, type, database, user_name, address, netmask, auth_method,
       error IS NOT NULL AS has_error
FROM pg_hba_file_rules ORDER BY rule_number;
COMMIT;
SQL
