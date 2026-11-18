#!/usr/bin/env python3
"""Prepare dedicated proxy/backend TLS identities; output is outside the repo."""
import argparse
import json
import os
from pathlib import Path
import tempfile
import shutil
import subprocess

import pki


def prepare(directory, password):
    directory = pki.external(directory)
    password_source = pki.secret(password)
    directory.mkdir(mode=0o700, parents=False, exist_ok=False)
    # This CA is ONLY for the private proxy channel; it cannot issue device identities.
    pki.run("req", "-new", "-x509", "-newkey", "rsa:4096", "-sha256", "-days", "1825",
            "-passout", password_source, "-keyout", directory / "internal-ca.key.pem",
            "-out", directory / "internal-ca.crt.pem", "-subj", "/CN=IMBASAC Internal Proxy Channel CA",
            "-addext", "basicConstraints=critical,CA:TRUE,pathlen:0",
            "-addext", "keyUsage=critical,keyCertSign,cRLSign")
    for name, common_name, usages, san in (
            ("backend", "imbasac-backend", "serverAuth", "DNS:localhost,IP:127.0.0.1,IP:::1"),
            ("nginx", "imbasac-nginx-proxy", "clientAuth", "URI:urn:imbasac:service:nginx")):
        with tempfile.TemporaryDirectory(dir=directory) as tmp:
            csr = Path(tmp) / "request.pem"
            ext = Path(tmp) / "extensions.cnf"
            pki.write_new(ext, "basicConstraints=critical,CA:FALSE\nkeyUsage=critical,digitalSignature,keyEncipherment\n"
                          "extendedKeyUsage=" + usages + "\nsubjectAltName=" + san + "\n")
            pki.run("req", "-new", "-newkey", "rsa:3072", "-nodes", "-sha256",
                    "-keyout", directory / (name + ".key.pem"), "-out", csr, "-subj", "/CN=" + common_name)
            pki.run("x509", "-req", "-in", csr, "-CA", directory / "internal-ca.crt.pem",
                    "-CAkey", directory / "internal-ca.key.pem", "-passin", password_source,
                    "-set_serial", "0x" + os.urandom(16).hex(), "-sha256", "-days", "180", "-extfile", ext,
                    "-out", directory / (name + ".crt.pem"))
            pki.run("verify", "-CAfile", directory / "internal-ca.crt.pem", "-purpose",
                    "sslserver" if name == "backend" else "sslclient", directory / (name + ".crt.pem"))
    fingerprint = pki.run("x509", "-in", directory / "nginx.crt.pem", "-sha256", "-fingerprint", "-noout").decode().split("=", 1)[1].strip().replace(":", "").lower()
    pki.write_new(directory / "public-metadata.json", json.dumps({"nginxCertificateSha256": fingerprint,
                  "proxySanUri": "urn:imbasac:service:nginx", "backendServerName": "localhost",
                  "activation": "PENDING_TLS_REHEARSAL"}, indent=2) + "\n")
    for path in directory.iterdir():
        if path.is_file():
            os.chmod(path, 0o600)
    # Names referenced by the reviewed Nginx fragment.
    (directory / "nginx.key.pem").rename(directory / "nginx-client.key")
    pki.write_new(directory / "nginx-client-chain.pem", (directory / "nginx.crt.pem").read_bytes() + (directory / "internal-ca.crt.pem").read_bytes())
    pki.write_new(directory / "root.crt", (directory / "internal-ca.crt.pem").read_bytes())
    return fingerprint


def prepare_java_stores(directory, password):
    directory = pki.external(directory)
    pass_source = pki.secret(password)
    password = str(Path(password).resolve())
    if (directory / "backend.p12").exists() or (directory / "proxy-trust.p12").exists():
        raise ValueError("Java stores already exist")
    keytool = os.environ.get("KEYTOOL", "keytool")
    if not shutil.which(keytool):
        raise ValueError("Java 21 keytool is required to prepare the dedicated truststore")
    pki.run("pkcs12", "-export", "-name", "imbasac-backend", "-inkey", directory / "backend.key.pem",
            "-in", directory / "backend.crt.pem", "-certfile", directory / "internal-ca.crt.pem",
            "-out", directory / "backend.p12", "-passout", pass_source)
    result = subprocess.run([keytool, "-importcert", "-noprompt", "-alias", "imbasac-internal-proxy-ca",
                             "-file", str(directory / "internal-ca.crt.pem"), "-keystore", str(directory / "proxy-trust.p12"),
                             "-storetype", "PKCS12", "-storepass:file", password], capture_output=True, timeout=60)
    if result.returncode:
        raise ValueError("Dedicated Java truststore creation failed")
    for name in ("backend.p12", "proxy-trust.p12"):
        os.chmod(directory / name, 0o600)


if __name__ == "__main__":
    os.umask(0o077)
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--directory", required=True)
    parser.add_argument("--passphrase-file", required=True)
    parser.add_argument("--prepare-java-stores", action="store_true")
    args = parser.parse_args()
    try:
        prepare(args.directory, args.passphrase_file)
        if args.prepare_java_stores:
            prepare_java_stores(args.directory, args.passphrase_file)
        print("Prepared outside repository; not installed or activated")
    except (ValueError, OSError) as error:
        parser.exit(2, str(error) + "\n")
