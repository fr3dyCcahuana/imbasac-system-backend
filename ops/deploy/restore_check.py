#!/usr/bin/env python3
"""Restore a dump ONLY in a new Docker container without network or host ports."""
import argparse
import json
from pathlib import Path
import subprocess
import time
import uuid


def docker(*args, allow_failure=False):
    result = subprocess.run(["docker", *map(str, args)], capture_output=True, text=True)
    if result.returncode and not allow_failure:
        raise ValueError("Isolated Docker restore operation failed; no production database was targeted")
    return result


def verify(dump, image="postgres:18-alpine"):
    dump = Path(dump).resolve(strict=True)
    if not dump.is_file():
        raise ValueError("Supply an extracted custom-format database.dump")
    # No arbitrary image or shell strings. Match the major version before a real rehearsal.
    if image not in ("postgres:18-alpine", "postgres:18"):
        raise ValueError("Only the reviewed PostgreSQL 18 image is allowed")
    container = "imbasac-restore-check-" + uuid.uuid4().hex
    created = False
    try:
        docker("create", "--pull=never", "--name", container, "--network", "none",
               "--env", "POSTGRES_HOST_AUTH_METHOD=trust", "--label", "imbasac.restore-check=true", image)
        created = True
        docker("start", container)
        deadline = time.monotonic() + 45
        while time.monotonic() < deadline:
            if docker("exec", container, "pg_isready", "-U", "postgres", allow_failure=True).returncode == 0:
                break
            time.sleep(0.5)
        else:
            raise ValueError("Disposable PostgreSQL did not become ready")
        docker("cp", str(dump), container + ":/tmp/database.dump")
        docker("exec", container, "createdb", "-U", "postgres", "imbasac_isolated_restore")
        docker("exec", container, "pg_restore", "-U", "postgres", "--dbname", "imbasac_isolated_restore",
               "--exit-on-error", "--no-owner", "--no-privileges", "/tmp/database.dump")
        result = docker("exec", container, "psql", "-U", "postgres", "-d", "imbasac_isolated_restore", "-X", "-At",
                        "-v", "ON_ERROR_STOP=1", "-c", "BEGIN READ ONLY; SELECT count(*) FROM pg_class WHERE relkind IN ('r','p'); COMMIT;")
        return {"databaseRestoreVerified": True, "applicationRestoreVerified": False,
                "offVpsCopyVerified": False, "rpoZeroVerified": False, "rtoOneHourVerified": False,
                "tableCount": int(next(line for line in result.stdout.splitlines() if line.isdigit()))}
    finally:
        if created:
            docker("rm", "--force", "--volumes", container, allow_failure=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dump", required=True)
    parser.add_argument("--image", default="postgres:18-alpine")
    args = parser.parse_args()
    try:
        print(json.dumps(verify(args.dump, args.image), indent=2))
    except (ValueError, OSError, StopIteration) as error:
        parser.exit(2, str(error) + "\n")
