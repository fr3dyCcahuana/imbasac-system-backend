#!/bin/sh
# Run only from the reviewed release bundle in an authorized Ubuntu console.
set -eu
PATH=/usr/sbin:/usr/bin:/sbin:/bin
export PATH
umask 077
[ "$#" -eq 0 ] || { echo 'INSTALL_ARGUMENTS_INVALID' >&2; exit 2; }
[ "$(id -u)" -eq 0 ] || { echo 'INSTALL_REQUIRES_ROOT' >&2; exit 2; }
source_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -P)
for tool in /usr/bin/python3 /usr/bin/openssl /usr/sbin/visudo /usr/sbin/useradd; do
  [ -x "$tool" ] || { echo 'INSTALL_TOOL_MISSING' >&2; exit 2; }
done
id posapp >/dev/null 2>&1 || { echo 'INSTALL_APP_USER_MISSING' >&2; exit 2; }
for target in /usr/local/lib/imbasac-ca/sign-request /usr/local/lib/imbasac-ca/pki.py /etc/imbasac/ca-signer.json /etc/sudoers.d/imbasac-ca-signer; do
  [ ! -e "$target" ] && [ ! -L "$target" ] || { echo 'INSTALL_EXISTING_FILE_REVIEW_REQUIRED' >&2; exit 2; }
done
if ! id imbasac-ca >/dev/null 2>&1; then
  useradd --system --user-group --home-dir /var/lib/imbasac-ca --no-create-home --shell /usr/sbin/nologin imbasac-ca
fi
ca_gid=$(getent group imbasac-ca | cut -d: -f3)
[ -n "$ca_gid" ] || { echo 'INSTALL_CA_GROUP_INVALID' >&2; exit 2; }
case " $(id -G posapp) " in *" $ca_gid "*) echo 'INSTALL_APP_MUST_NOT_JOIN_CA_GROUP' >&2; exit 2;; esac
[ "$(id -gn imbasac-ca)" = imbasac-ca ] || { echo 'INSTALL_CA_GROUP_INVALID' >&2; exit 2; }
for directory in /usr/local/lib/imbasac-ca /etc/imbasac /var/lib/imbasac-ca; do
  [ ! -L "$directory" ] || { echo 'INSTALL_SYMLINK_FORBIDDEN' >&2; exit 2; }
done
install -d -o root -g root -m 0755 /usr/local/lib/imbasac-ca
# Preserve an existing /etc/imbasac directory's ownership/mode; wrapper validates all ancestors.
if [ ! -d /etc/imbasac ]; then install -d -o root -g root -m 0755 /etc/imbasac; fi
if [ ! -d /var/lib/imbasac-ca ]; then install -d -o root -g imbasac-ca -m 0750 /var/lib/imbasac-ca; fi
for directory in /var/lib/imbasac-ca/requests /var/lib/imbasac-ca/private; do
  if [ ! -d "$directory" ]; then install -d -o imbasac-ca -g imbasac-ca -m 0700 "$directory"; fi
done
install -o root -g root -m 0644 "$source_dir/pki.py" /usr/local/lib/imbasac-ca/pki.py
install -o root -g root -m 0755 "$source_dir/sign-request" /usr/local/lib/imbasac-ca/sign-request
install -o root -g imbasac-ca -m 0640 "$source_dir/ca-signer.example.json" /etc/imbasac/ca-signer.json
staged=$(mktemp /etc/sudoers.d/.imbasac-ca-signer.XXXXXX)
trap 'rm -f -- "$staged"' EXIT HUP INT TERM
install -o root -g root -m 0440 "$source_dir/imbasac-ca-signer.sudoers" "$staged"
visudo -cf "$staged" >/dev/null
install -o root -g root -m 0440 "$staged" /etc/sudoers.d/imbasac-ca-signer
if ! visudo -c >/dev/null; then
  rm -f -- /etc/sudoers.d/imbasac-ca-signer
  echo 'INSTALL_SUDOERS_REJECTED' >&2
  exit 2
fi
echo 'Signer bridge installed. CA keys/issuer/passphrase, application flags and hardware approval remain pending.'
