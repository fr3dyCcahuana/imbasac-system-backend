#!/usr/bin/env python3
"""Validate a root+issuer CRL bundle and atomically replace Nginx's public copy."""
import argparse
import datetime as dt
import os
from pathlib import Path
import subprocess
import tempfile

import pki


def validate(crl, ca):
    pki.run("crl", "-in", crl, "-verify", "-CAfile", ca, "-noout")
    last = pki.run("crl", "-in", crl, "-lastupdate", "-noout").decode().strip().split("=", 1)[1]
    issued = dt.datetime.strptime(last, "%b %d %H:%M:%S %Y %Z").replace(tzinfo=dt.timezone.utc)
    value = pki.run("crl", "-in", crl, "-nextupdate", "-noout").decode().strip().split("=", 1)[1]
    expires = dt.datetime.strptime(value, "%b %d %H:%M:%S %Y %Z").replace(tzinfo=dt.timezone.utc)
    now = dt.datetime.now(dt.timezone.utc)
    if issued > now or expires <= issued:
        raise ValueError("CRL timestamps are not currently usable")
    if expires <= now + dt.timedelta(hours=24):
        raise ValueError("CRL must retain at least 24 hours of validity")


def publish(root_crl, root_ca, issuer_crl, issuer_ca, target, reload_nginx=False):
    if Path(target).is_symlink():
        raise ValueError("Target must not be a symlink")
    target = pki.external(target)
    if target.is_symlink() or not target.parent.is_dir():
        raise ValueError("Target must have an existing directory and must not be a symlink")
    with pki.locked(target.parent):
        _publish(root_crl, root_ca, issuer_crl, issuer_ca, target, reload_nginx)


def _publish(root_crl, root_ca, issuer_crl, issuer_ca, target, reload_nginx):
    # Snapshot before verification: generation may atomically replace the spool
    # while the root publisher works. Publish exactly the bytes that were checked.
    with tempfile.TemporaryDirectory(prefix="imbasac-crl-snapshot-") as snapshot:
        inputs = []
        for index, original in enumerate((root_crl, root_ca, issuer_crl, issuer_ca)):
            limit = 8 * 1024 * 1024 if index in (0, 2) else 65536
            with Path(original).open("rb") as stream:
                contents = stream.read(limit + 1)
            if len(contents) > limit:
                raise ValueError("CRL/certificate exceeds the publication limit")
            saved = Path(snapshot) / str(index)
            pki.write_new(saved, contents)
            inputs.append(saved)
        root_crl, root_ca, issuer_crl, issuer_ca = inputs
        pki.run("verify", "-CAfile", root_ca, issuer_ca)
        validate(root_crl, root_ca)
        validate(issuer_crl, issuer_ca)
        bundle = root_crl.read_bytes().rstrip() + b"\n" + issuer_crl.read_bytes().rstrip() + b"\n"
    def replace(data):
        fd, temporary = tempfile.mkstemp(prefix=".crl-", dir=target.parent)
        try:
            with os.fdopen(fd, "wb") as stream:
                stream.write(data)
                stream.flush()
                os.fsync(stream.fileno())
            os.chmod(temporary, 0o644)  # CRL contains public information only.
            os.replace(temporary, target)
            if os.name == "posix":
                descriptor = os.open(target.parent, os.O_RDONLY)
                try:
                    os.fsync(descriptor)
                finally:
                    os.close(descriptor)
        finally:
            if os.path.exists(temporary):
                os.unlink(temporary)
    previous = target.read_bytes() if target.exists() else None
    if reload_nginx and previous is None:
        raise ValueError("First CRL installation requires separate configuration rehearsal")
    replace(bundle)
    if reload_nginx:
        try:
            subprocess.run(["nginx", "-t"], check=True, capture_output=True, timeout=15)
            subprocess.run(["systemctl", "reload", "nginx"], check=True, capture_output=True, timeout=15)
        except (subprocess.CalledProcessError, subprocess.TimeoutExpired, OSError):
            replace(previous)
            raise ValueError("Nginx test/reload failed; previous CRL restored. Diagnose before retry") from None


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    for option in ("root-crl", "root-ca", "issuer-crl", "issuer-ca", "target"):
        parser.add_argument("--" + option, required=True)
    parser.add_argument("--reload-nginx", action="store_true")
    args = parser.parse_args()
    try:
        publish(args.root_crl, args.root_ca, args.issuer_crl, args.issuer_ca, args.target, args.reload_nginx)
    except (OSError, ValueError) as error:
        parser.exit(2, str(error) + "\n")
