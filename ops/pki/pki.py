#!/usr/bin/env python3
"""Manual, serialized OpenSSL CA operations. Never a web-facing signing API."""
import argparse
import contextlib
import datetime as dt
import json
import os
from pathlib import Path
import re
import shutil
import socket
import subprocess
import tempfile
import uuid

REPOSITORY = Path(__file__).resolve().parents[2]


def external(path):
    path = Path(path).expanduser().resolve()
    if path == REPOSITORY or REPOSITORY in path.parents:
        raise ValueError("PKI state/outputs must be outside this repository")
    if any(c in str(path) for c in ('\n', '\r', '"', '$')):
        raise ValueError("Unsupported path characters")
    return path


def run(*args, data=None):
    result = subprocess.run([os.environ.get("OPENSSL", "openssl"), *map(str, args)],
                            input=data, capture_output=True, timeout=120)
    if result.returncode:
        # No subprocess stderr: it may contain request data or sensitive paths.
        raise ValueError("OpenSSL operation failed: " + str(args[0]))
    return result.stdout


def secret(path):
    if path is None or Path(path).is_symlink():
        raise ValueError("A non-symlink passphrase file is required")
    path = external(path)
    if path.is_symlink() or not path.is_file() or not path.read_bytes().strip():
        raise ValueError("Passphrase file is missing or empty")
    if os.name == "posix" and path.stat().st_mode & 0o077:
        raise ValueError("Passphrase file must have mode 0600 or stricter")
    return "file:" + str(path)


@contextlib.contextmanager
def locked(directory):
    lock = directory / ".operator.lock"
    fd = os.open(lock, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
    try:
        os.write(fd, str(os.getpid()).encode())
        yield
    finally:
        os.close(fd)
        lock.unlink()


def write_new(path, data, mode=0o600):
    fd = os.open(path, os.O_CREAT | os.O_EXCL | os.O_WRONLY, mode)
    with os.fdopen(fd, "wb") as output:
        output.write(data.encode() if isinstance(data, str) else data)


def config(directory, kind):
    # The supplied DN and extensions never come from the CSR.
    text = f'''[ca]
default_ca = ca_default
[ca_default]
dir = "{directory.as_posix()}"
database = $dir/index.txt
new_certs_dir = $dir/newcerts
certificate = $dir/ca.crt.pem
private_key = $dir/private/ca.key.pem
serial = $dir/serial
crlnumber = $dir/crlnumber
default_md = sha256
default_days = 365
default_crl_days = {180 if kind == 'root' else 7}
policy = policy
unique_subject = no
copy_extensions = none
crl_extensions = crl_ext
[policy]
commonName = supplied
[crl_ext]
authorityKeyIdentifier = keyid:always
[root]
basicConstraints = critical,CA:true,pathlen:1
keyUsage = critical,keyCertSign,cRLSign
subjectKeyIdentifier = hash
authorityKeyIdentifier = keyid:always
[issuer]
basicConstraints = critical,CA:true,pathlen:0
keyUsage = critical,keyCertSign,cRLSign
subjectKeyIdentifier = hash
authorityKeyIdentifier = keyid:always
'''
    write_new(directory / "openssl.cnf", text)
    write_new(directory / "kind", kind)


def initialize(directory, kind, password, offline=False):
    directory = external(directory)
    if kind == "root" and (not offline or socket.gethostname().lower().startswith("srv1283651")):
        raise ValueError("Root creation requires an offline workstation, never the VPS")
    password = secret(password)
    directory.mkdir(mode=0o700, parents=False, exist_ok=False)
    (directory / "private").mkdir(mode=0o700)
    (directory / "newcerts").mkdir(mode=0o700)
    config(directory, kind)
    write_new(directory / "index.txt", "")
    write_new(directory / "serial", "1000\n")
    write_new(directory / "crlnumber", "1000\n")
    run("genpkey", "-algorithm", "RSA", "-pkeyopt", "rsa_keygen_bits:4096",
        "-aes-256-cbc", "-pass", password, "-out", directory / "private/ca.key.pem")
    os.chmod(directory / "private/ca.key.pem", 0o600)
    if kind == "root":
        run("req", "-new", "-x509", "-sha256", "-days", "3650", "-config", directory / "openssl.cnf",
            "-extensions", "root", "-key", directory / "private/ca.key.pem", "-passin", password,
            "-subj", "/CN=IMBASAC Offline Root CA", "-out", directory / "ca.crt.pem")
    else:
        run("req", "-new", "-sha256", "-key", directory / "private/ca.key.pem", "-passin", password,
            "-subj", "/CN=IMBASAC Device Issuing CA", "-out", directory / "issuer.csr.pem")
    return directory


def validate_csr(csr):
    csr = Path(csr).resolve()
    if not csr.is_file() or csr.stat().st_size > 16384:
        raise ValueError("CSR must be a file <=16 KiB")
    run("req", "-in", csr, "-verify", "-noout")
    text = run("req", "-in", csr, "-text", "-noout").decode("ascii", "replace")
    # Inspect the public key independently: CSR names/attributes cannot inject a
    # fake curve/modulus line into this OpenSSL output.
    public_key = run("req", "-in", csr, "-pubkey", "-noout")
    key_text = run("pkey", "-pubin", "-text_pub", "-noout", data=public_key).decode("ascii", "replace")
    rsa = (re.search(r"^\s*Public Key Algorithm: rsaEncryption\s*$", text, re.MULTILINE)
           and re.search(r"^Public-Key: \((3072|4096) bit\)\s*$", key_text, re.MULTILINE))
    p256 = (re.search(r"^\s*Public Key Algorithm: id-ecPublicKey\s*$", text, re.MULTILINE)
            and re.search(r"^Public-Key: \(256 bit\)\s*$", key_text, re.MULTILINE)
            and re.search(r"^ASN1 OID: prime256v1\s*$", key_text, re.MULTILINE))
    if not rsa and not p256:
        raise ValueError("Only RSA3072/RSA4096 or named EC P-256 CSRs are accepted")
    signature = (r"^\s*Signature Algorithm: sha(256|384|512)WithRSAEncryption\s*$" if rsa
                 else r"^\s*Signature Algorithm: ecdsa-with-SHA256\s*$")
    if not re.search(signature, text, re.MULTILINE):
        raise ValueError("CSR signature requires RSA SHA256+ or ECDSA SHA256 for P-256")
    return csr


def assert_kind(directory, expected):
    directory = external(directory)
    if (directory / "kind").read_text() != expected:
        raise ValueError("Unexpected CA kind")
    if directory.stat().st_mode & 0o077 and os.name == "posix":
        raise ValueError("CA directory must be private (0700)")
    return directory


def not_after(cert):
    value = run("x509", "-in", cert, "-enddate", "-noout").decode().strip().split("=", 1)[1]
    return dt.datetime.strptime(value, "%b %d %H:%M:%S %Y %Z").replace(tzinfo=dt.timezone.utc)


def lifetime(ca_cert, days):
    if dt.datetime.now(dt.timezone.utc) + dt.timedelta(days=days, hours=1) >= not_after(ca_cert):
        raise ValueError("Requested lifetime reaches or exceeds CA expiry")


def sign_issuer(root, csr, output, password, offline=False):
    if not offline or socket.gethostname().lower().startswith("srv1283651"):
        raise ValueError("Issuer signing requires the offline root workstation")
    root = assert_kind(root, "root")
    csr = validate_csr(csr)
    output = external(output)
    if output.exists():
        raise ValueError("Output already exists")
    lifetime(root / "ca.crt.pem", 1825)
    with locked(root):
        run("ca", "-batch", "-config", root / "openssl.cnf", "-passin", secret(password),
            "-in", csr, "-out", output, "-notext", "-extensions", "issuer", "-days", "1825",
            "-subj", "/CN=IMBASAC Device Issuing CA", "-rand_serial")


def install_issuer(directory, certificate, root_certificate):
    directory = assert_kind(directory, "issuer")
    certificate = Path(certificate).resolve()
    root_certificate = Path(root_certificate).resolve()
    if (directory / "ca.crt.pem").exists() or (directory / "root.crt.pem").exists():
        raise ValueError("Issuer chain already installed")
    run("verify", "-CAfile", root_certificate, certificate)
    text = run("x509", "-in", certificate, "-text", "-noout").decode()
    if "CA:TRUE, pathlen:0" not in text:
        raise ValueError("Issuer must have CA:true,pathlen:0")
    expected = run("req", "-in", directory / "issuer.csr.pem", "-pubkey", "-noout")
    if expected != run("x509", "-in", certificate, "-pubkey", "-noout"):
        raise ValueError("Issuer certificate does not match issuer key CSR")
    write_new(directory / "ca.crt.pem", certificate.read_bytes())
    write_new(directory / "root.crt.pem", root_certificate.read_bytes())


def issue_device(directory, csr, device_id, output, password, days=365):
    directory = assert_kind(directory, "issuer")
    device_id = str(uuid.UUID(device_id))
    if not 1 <= days <= 365:
        raise ValueError("Device lifetime must be 1..365 days")
    csr = validate_csr(csr)
    output = external(output)
    if output.exists() or output.with_suffix(".metadata.json").exists():
        raise ValueError("Output or metadata already exists")
    lifetime(directory / "ca.crt.pem", days)
    with locked(directory), tempfile.TemporaryDirectory(dir=directory) as tmp:
        extensions = Path(tmp) / "leaf.cnf"
        write_new(extensions, f'''[device]
basicConstraints = critical,CA:false
keyUsage = critical,digitalSignature
extendedKeyUsage = clientAuth
subjectKeyIdentifier = hash
authorityKeyIdentifier = keyid:always
subjectAltName = URI:urn:imbasac:device:{device_id}
''')
        run("ca", "-batch", "-config", directory / "openssl.cnf", "-passin", secret(password),
            "-in", csr, "-out", output, "-notext", "-extfile", extensions, "-extensions", "device",
            "-days", str(days), "-subj", "/CN=imbasac-device-" + device_id, "-rand_serial")
        run("verify", "-CAfile", directory / "root.crt.pem", "-untrusted", directory / "ca.crt.pem",
            "-purpose", "sslclient", output)
        serial = run("x509", "-in", output, "-serial", "-noout").decode().strip().split("=", 1)[1]
        fingerprint = run("x509", "-in", output, "-sha256", "-fingerprint", "-noout").decode().strip().split("=", 1)[1].replace(":", "").lower()
        metadata = {"deviceId": device_id, "serialHex": serial.lower(), "fingerprintSha256": fingerprint,
                    "sanUri": "urn:imbasac:device:" + device_id, "activation": "PENDING_TLS_AND_HARDWARE_REVIEW",
                    "hardwareAttestationVerified": False, "notAfter": not_after(output).isoformat()}
        write_new(output.with_suffix(".metadata.json"), json.dumps(metadata, indent=2) + "\n")
        return metadata


def validate_revocation(directory, certificate):
    directory = assert_kind(directory, "issuer")
    # Exact configured issuing CA, including already expired leaves. Revocation
    # records must remain possible after a credential's expiry.
    run("verify", "-trusted", directory / "ca.crt.pem", "-partial_chain", "-no_check_time", "-purpose", "sslclient", certificate)
    profile = run("x509", "-in", certificate, "-text", "-noout").decode()
    if "CA:FALSE" not in profile or "TLS Web Client Authentication" not in profile:
        raise ValueError("Only this issuer's client leaf certificates can be revoked")
    serial = run("x509", "-in", certificate, "-serial", "-noout").decode().strip().split("=", 1)[1]
    if not re.fullmatch(r"[0-9A-Fa-f]{1,40}", serial):
        raise ValueError("Invalid leaf serial")
    issued = directory / "newcerts" / (serial.upper() + ".pem")
    if not issued.is_file() or run("x509", "-in", issued, "-outform", "DER") != run("x509", "-in", certificate, "-outform", "DER"):
        raise ValueError("Certificate is not an exact recorded leaf of this issuer")
    return serial


def revoke(directory, certificate, password, reason="keyCompromise"):
    directory = assert_kind(directory, "issuer")
    if reason not in ("keyCompromise", "cessationOfOperation", "superseded"):
        raise ValueError("Unsupported revocation reason")
    serial = validate_revocation(directory, certificate)
    with locked(directory):
        entries = [line.split("\t") for line in (directory / "index.txt").read_text().splitlines()]
        states = [entry[0] for entry in entries if len(entry) >= 6 and int(entry[3], 16) == int(serial, 16)]
        if len(states) != 1:
            raise ValueError("CA index state is missing or ambiguous")
        if states[0] == "R":
            return False
        if states[0] not in ("V", "E"):
            raise ValueError("Unexpected CA index state")
        run("ca", "-config", directory / "openssl.cnf", "-passin", secret(password),
            "-revoke", certificate, "-crl_reason", reason)
        return True


def generate_crl(directory, output, password):
    directory = external(directory)
    if (directory / "kind").read_text() not in ("root", "issuer"):
        raise ValueError("Not a CA")
    output = external(output)
    if output.exists():
        raise ValueError("Output already exists; retain previous CRL until replacement verified")
    with locked(directory):
        run("ca", "-config", directory / "openssl.cnf", "-passin", secret(password), "-gencrl", "-out", output)
        run("crl", "-in", output, "-verify", "-CAfile", directory / "ca.crt.pem", "-noout")


def main():
    os.umask(0o077)
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("init-root", "init-issuer", "sign-issuer", "install-issuer", "issue-device", "revoke", "crl"))
    parser.add_argument("--directory", required=True)
    parser.add_argument("--passphrase-file")
    parser.add_argument("--csr")
    parser.add_argument("--certificate")
    parser.add_argument("--root-certificate")
    parser.add_argument("--device-id")
    parser.add_argument("--output")
    parser.add_argument("--days", type=int, default=365)
    parser.add_argument("--confirm-offline-workstation", action="store_true")
    args = parser.parse_args()
    try:
        if args.action.startswith("init-"):
            initialize(args.directory, args.action[5:], args.passphrase_file, args.confirm_offline_workstation)
        elif args.action == "sign-issuer":
            sign_issuer(args.directory, args.csr, args.output, args.passphrase_file, args.confirm_offline_workstation)
        elif args.action == "install-issuer":
            install_issuer(args.directory, args.certificate, args.root_certificate)
        elif args.action == "issue-device":
            issue_device(args.directory, args.csr, args.device_id, args.output, args.passphrase_file, args.days)
        elif args.action == "revoke":
            revoke(args.directory, args.certificate, args.passphrase_file)
        elif args.action == "crl":
            generate_crl(args.directory, args.output, args.passphrase_file)
        print(json.dumps({"result": "prepared", "productionActivation": False}))
    except (ValueError, OSError, TypeError) as error:
        parser.exit(2, str(error) + "\n")


if __name__ == "__main__":
    main()
