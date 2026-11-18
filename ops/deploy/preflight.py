#!/usr/bin/env python3
"""Limited, read-only Ubuntu preflight. Does not read environment files or secrets."""
import argparse
import json
import os
import re
import shutil
import subprocess


def check(argv):
    try:
        result = subprocess.run(argv, capture_output=True, text=True, timeout=15)
        return result.returncode == 0
    except (OSError, subprocess.TimeoutExpired):
        return False


def clock_synchronized():
    try:
        result = subprocess.run(["timedatectl", "show", "--property=NTPSynchronized", "--value"],
                                capture_output=True, text=True, timeout=15)
        return result.returncode == 0 and result.stdout.strip() == "yes"
    except (OSError, subprocess.TimeoutExpired):
        return False


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--database", required=True)
    parser.add_argument("--port", type=int, default=5432)
    parser.add_argument("--service", default="pos-backend.service")
    args = parser.parse_args()
    if not re.fullmatch(r"[A-Za-z0-9_][A-Za-z0-9_-]{0,62}", args.database) or not re.fullmatch(r"[A-Za-z0-9_.@-]+\.service", args.service):
        parser.error("Invalid database/service name")
    if os.name != "posix" or os.geteuid() != 0:
        parser.error("Run on Ubuntu through an administrator console")
    report = {"readOnly": True, "rpoZeroVerified": False, "rtoOneHourVerified": False,
              "deploymentApproved": False, "tools": {name: bool(shutil.which(name)) for name in
              ("nginx", "systemctl", "pg_dump", "pg_restore", "gpg", "runuser")}}
    report["nginxConfigurationValid"] = check(["nginx", "-t"])
    report["backendActive"] = check(["systemctl", "is-active", "--quiet", args.service])
    report["clockSynchronized"] = clock_synchronized()
    # No remote hostname or inherited PG URL; read-only transaction on the local socket.
    sql = """BEGIN READ ONLY; SET LOCAL statement_timeout='5s';
SELECT json_build_object('serverVersion',current_setting('server_version'),
 'auditSessionTable',to_regclass('public.security_access_session') IS NOT NULL,
 'auditEventTable',to_regclass('public.security_access_event') IS NOT NULL,
 'deviceTable',to_regclass('public.security_device') IS NOT NULL);
COMMIT;"""
    command = ["runuser", "-u", "postgres", "--", "psql", "-X", "-w", "-h", "/var/run/postgresql", "-p", str(args.port),
               "-d", args.database, "-At", "-v", "ON_ERROR_STOP=1", "-c", sql]
    try:
        result = subprocess.run(command, capture_output=True, text=True, timeout=15,
                                env={"PATH": os.environ.get("PATH", "/usr/bin:/bin"), "LC_ALL": "C"})
    except (OSError, subprocess.TimeoutExpired):
        result = None
    report["postgresReadOnlyCheck"] = result is not None and result.returncode == 0
    if report["postgresReadOnlyCheck"]:
        for line in result.stdout.splitlines():
            if line.startswith("{"):
                report["schemaObservation"] = json.loads(line)
    report["spaceAvailableBytes"] = shutil.disk_usage("/var/backups").free
    print(json.dumps(report, indent=2))
    return 0 if report["nginxConfigurationValid"] and report["backendActive"] and report["clockSynchronized"] and report["postgresReadOnlyCheck"] and all(report["tools"].values()) else 2


if __name__ == "__main__":
    raise SystemExit(main())
