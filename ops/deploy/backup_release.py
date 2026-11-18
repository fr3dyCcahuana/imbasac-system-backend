#!/usr/bin/env python3
"""Create a restricted, encrypted release backup; no writes to PostgreSQL."""
import argparse
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tarfile
import tempfile

REPOSITORY = Path(__file__).resolve().parents[2]


def private_external(path):
    path = Path(path).expanduser().resolve()
    if path == REPOSITORY or REPOSITORY in path.parents:
        raise ValueError("Backup state must remain outside the repository")
    return path


def validate_selection(manifest):
    selected = json.loads(Path(manifest).read_text())
    if not isinstance(selected, list) or not selected:
        raise ValueError("File selection must be a non-empty JSON array")
    paths = []
    for raw in selected:
        supplied = Path(raw)
        path = supplied.resolve()
        if not supplied.is_absolute() or not path.exists() or supplied.is_symlink():
            raise ValueError("Select existing absolute paths; resolve symlinks explicitly")
        if path == Path(path.anchor) or path in (Path("/root"), Path("/home"), Path("/var"), Path("/etc"), Path("/opt")):
            raise ValueError("Select specific application/configuration paths, not top-level directories")
        if path == REPOSITORY or REPOSITORY in path.parents:
            raise ValueError("Do not archive the development repository")
        paths.append(path)
    return paths


def execute(command, stdout=None):
    result = subprocess.run(command, stdout=stdout if stdout is not None else subprocess.PIPE,
                            stderr=subprocess.PIPE, timeout=600,
                            env={"PATH": os.environ.get("PATH", "/usr/bin:/bin"), "LC_ALL": "C"})
    if result.returncode:
        raise ValueError("Backup tool failed; partial result is not a verified backup")


def digest_file(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def backup(database, port, selection, destination, passphrase_file, writers_paused):
    if os.name != "posix" or os.geteuid() != 0:
        raise ValueError("Run through the Ubuntu administrator console")
    if not writers_paused:
        raise ValueError("Pause/drain writers and explicitly acknowledge the release cut first")
    if not re.fullmatch(r"[A-Za-z0-9_][A-Za-z0-9_-]{0,62}", database) or not 1 <= port <= 65535:
        raise ValueError("Invalid local database/port")
    paths = validate_selection(selection)
    destination = private_external(destination)
    password = private_external(passphrase_file)
    if not password.is_file() or password.stat().st_mode & 0o077 or not password.read_bytes().strip():
        raise ValueError("Passphrase must be in a non-empty file mode 0600 or stricter")
    if destination.exists():
        raise ValueError("Backup destination must be new; existing files are never overwritten")
    for path in paths:
        if path == destination or path in destination.parents:
            raise ValueError("Backup output cannot be inside the selected files")
    destination.mkdir(mode=0o700, parents=False, exist_ok=False)
    encrypted = destination / "release-backup.tar.gpg"
    try:
        with tempfile.TemporaryDirectory(prefix=".plaintext-", dir=destination) as temporary:
            temporary = Path(temporary)
            dump = temporary / "database.dump"
            with dump.open("xb") as stream:
                execute(["runuser", "-u", "postgres", "--", "pg_dump", "-w", "-h", "/var/run/postgresql", "-p", str(port),
                         "-d", database, "--format=custom", "--no-owner", "--no-privileges", "--lock-wait-timeout=10s"], stdout=stream)
            execute(["pg_restore", "--list", str(dump)])
            files = temporary / "selected-files.tar"
            with tarfile.open(files, "w") as archive:
                for path in paths:
                    archive.add(path, arcname=str(path).lstrip("/"), recursive=True)
            metadata = {"createdUtc": dt.datetime.now(dt.timezone.utc).isoformat(), "database": database,
                        "writerPauseAcknowledgedByOperator": True, "restoreVerified": False,
                        "offVpsCopyVerified": False, "rpoZeroVerified": False,
                        "files": [{"name": path.name, "sha256": digest_file(path)} for path in (dump, files)]}
            (temporary / "manifest.json").write_text(json.dumps(metadata, indent=2) + "\n")
            package = temporary / "release-backup.tar"
            with tarfile.open(package, "w") as archive:
                for name in ("database.dump", "selected-files.tar", "manifest.json"):
                    archive.add(temporary / name, arcname=name, recursive=False)
            gpg_home = temporary / "gpg-home"
            gpg_home.mkdir(mode=0o700)
            execute(["gpg", "--batch", "--no-options", "--homedir", str(gpg_home), "--pinentry-mode", "loopback", "--passphrase-file", str(password),
                     "--symmetric", "--cipher-algo", "AES256", "--output", str(encrypted), str(package)])
        os.chmod(encrypted, 0o600)
        with encrypted.open("rb") as stream:
            digest = hashlib.file_digest(stream, "sha256").hexdigest()
        (destination / "SHA256SUMS").write_text(digest + "  release-backup.tar.gpg\n")
        os.chmod(destination / "SHA256SUMS", 0o600)
    except Exception:
        if encrypted.exists():
            encrypted.unlink()  # Exact file created by this invocation; never recursive deletion.
        raise
    return encrypted


if __name__ == "__main__":
    os.umask(0o077)
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--database", required=True)
    parser.add_argument("--port", type=int, default=5432)
    parser.add_argument("--selection-json", required=True)
    parser.add_argument("--destination", required=True)
    parser.add_argument("--passphrase-file", required=True)
    parser.add_argument("--acknowledge-writers-paused", action="store_true")
    args = parser.parse_args()
    try:
        backup(args.database, args.port, args.selection_json, args.destination, args.passphrase_file, args.acknowledge_writers_paused)
        print("Encrypted backup created; off-VPS copy and isolated restore still required")
    except (OSError, ValueError, tarfile.TarError, subprocess.TimeoutExpired) as error:
        parser.exit(2, str(error) + "\n")
