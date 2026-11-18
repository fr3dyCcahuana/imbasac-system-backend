#!/usr/bin/env python3
"""Read-only Ubuntu inventory. No environment, passwords, sales or full config dumps."""
import argparse
import json
import os
from pathlib import Path
import platform
import re
import shutil
import subprocess
from datetime import datetime, timezone


def run_command(args, *, env=None):
    try:
        result = subprocess.run(args, capture_output=True, text=True, timeout=12, env=env, check=False)
        # Error messages can contain connection details; only emit the exit code.
        if result.returncode != 0:
            return {"status": "unavailable", "exit_code": result.returncode}
        return {"status": "ok", "output": result.stdout.strip()[:12000]}
    except (OSError, subprocess.TimeoutExpired):
        return {"status": "unavailable"}


def assess_postgres(snapshot):
    settings_ok = (snapshot.get("fsync") == "on" and snapshot.get("full_page_writes") == "on"
                   and snapshot.get("wal_level") in ("replica", "logical")
                   and snapshot.get("synchronous_commit") in ("on", "remote_apply")
                   and bool(snapshot.get("synchronous_standby_names", "").strip())
                   and snapshot.get("in_recovery") is False)
    streaming = any(replica.get("state") == "streaming"
                    and replica.get("sync_state") in ("sync", "quorum")
                    and replica.get("has_flush_lsn") is True for replica in snapshot.get("replicas", []))
    return {
        "configuration_candidate": settings_ok and streaming,
        "independent_destination_verified": False,
        "application_transaction_settings_verified": False,
        "remote_storage_durability_verified": False,
        "restore_drill_verified": False,
        "rpo_zero_verified": False,
        "rto_one_hour_verified": False,
        "note": "A snapshot never certifies RPO/RTO. Verify all required synchronous acknowledgements, "
                "remote failure domain and storage, sale transactions, fencing and recovery drills.",
    }


def read_postgres(database, port):
    env = {key: value for key, value in os.environ.items() if not key.startswith("PG")}
    env.update(PGCONNECT_TIMEOUT="3", PGOPTIONS="-c default_transaction_read_only=on")
    sql = Path(__file__).with_name("postgres-readonly.sql").read_text(encoding="utf-8")
    args = ["psql", "-X", "-q", "-A", "-t", "-w", "--host=/var/run/postgresql",
            "--username=postgres", "--dbname=" + database, "--port=" + str(port),
            "--set=ON_ERROR_STOP=1", "--command=" + sql]
    if hasattr(os, "geteuid") and os.geteuid() == 0:
        args = ["runuser", "-u", "postgres", "--"] + args
    result = run_command(args, env=env)
    if result["status"] != "ok":
        return result
    try:
        snapshot = json.loads(result["output"])
        return {"status": "ok", "snapshot": snapshot, "assessment": assess_postgres(snapshot)}
    except (ValueError, TypeError):
        return {"status": "unavailable", "reason": "Unexpected diagnostic format"}


def inventory(services, database=None, port=5432):
    disk = shutil.disk_usage(Path.cwd().anchor)
    result = {
        "collected_at_utc": datetime.now(timezone.utc).isoformat(),
        "scope": "Read-only local host inventory; no production changes or recovery drill",
        "os": {"system": platform.system(), "release": platform.release()},
        "capacity": {"logical_cpus": os.cpu_count(), "disk_total_bytes": disk.total,
                     "disk_free_bytes": disk.free},
        "listeners": run_command(["ss", "-lntH"]),
        "nginx_config_test": run_command(["nginx", "-t"]),
        "postgres_clusters": run_command(["pg_lsclusters", "--no-header"]),
        "services": {},
        "rpo_zero_verified": False,
        "rto_one_hour_verified": False,
    }
    memory = Path("/proc/meminfo")
    if memory.is_file():
        result["capacity"]["memory"] = [line for line in memory.read_text().splitlines()
                                              if line.startswith(("MemTotal:", "MemAvailable:", "SwapTotal:"))]
    for service in services:
        result["services"][service] = run_command(["systemctl", "show", service,
            "--property=Id,LoadState,ActiveState,SubState,User,Group,WorkingDirectory,FragmentPath,Restart,NoNewPrivileges,ProtectSystem,PrivateTmp"])
    result["postgres"] = read_postgres(database, port) if database else {"status": "not_requested"}
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--service", action="append", default=[], help="Explicit systemd unit; repeat as needed")
    parser.add_argument("--pg-database", help="Opt in to read-only PostgreSQL checks via the local Unix socket")
    parser.add_argument("--pg-port", type=int, default=5432)
    args = parser.parse_args()
    if any(not re.fullmatch(r"[a-zA-Z0-9_.@-]+\.service", name) for name in args.service):
        parser.error("Service names must be explicit .service units")
    if args.pg_database and not re.fullmatch(r"[a-zA-Z0-9_][a-zA-Z0-9_.-]*", args.pg_database):
        parser.error("Provide a database name, never a connection URL or password")
    if not 1 <= args.pg_port <= 65535:
        parser.error("Invalid local PostgreSQL port")
    if platform.system() != "Linux":
        parser.error("Run the inventory on the Ubuntu VPS; tests can run on any platform")
    report = inventory(args.service, args.pg_database, args.pg_port)
    print(json.dumps(report, ensure_ascii=False, indent=2))
    unavailable = report["listeners"]["status"] != "ok" or report["nginx_config_test"]["status"] != "ok"
    unavailable |= any(item["status"] != "ok" or "LoadState=not-found" in item.get("output", "")
                       for item in report["services"].values())
    unavailable |= args.pg_database is not None and report["postgres"]["status"] != "ok"
    return 2 if unavailable else 0


if __name__ == "__main__":
    raise SystemExit(main())
