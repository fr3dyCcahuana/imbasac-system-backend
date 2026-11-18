#!/bin/sh
set -eu
# Read-only compatibility inventory. Keychain Assistant must create its key on this Mac.
if [ "$(uname -s)" != Darwin ]; then
  echo 'Run this inventory only on the Mac being homologated.' >&2
  exit 2
fi
printf 'macOS version: '
sw_vers -productVersion
printf 'Architecture: '
uname -m
printf 'Apple certificate tools available: '
command -v security >/dev/null && echo yes
cat <<'EOF'
Candidate steps (no credential is created by this inventory):
1. Open Keychain Access > Certificate Assistant > Request a Certificate From a CA.
2. Save CSR to disk using a reviewed standard provider: RSA 3072/4096 or named
   EC P-256 (prime256v1/secp256r1) with ECDSA SHA256 according to the approved profile.
3. Keep the generated private key on this Mac. Send only CSR to the administrator.
4. Import the signed public certificate into the same keychain/profile.
5. Test browser mTLS and copy-resistance with the administrator before activation.
IMPORTANT: A Keychain CSR does not attest Secure Enclave/non-exportability.
Do not approve hardware automatically. Apple-managed attestation needs separate
ACME/device-management integration and compatible hardware, which is not installed.
EOF
