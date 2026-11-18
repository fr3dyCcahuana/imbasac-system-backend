#!/bin/sh
# Installs reviewed files only: no daemon-reload, service start or timer enable.
set -eu
PATH=/usr/sbin:/usr/bin:/sbin:/bin
export PATH
umask 077
[ "$#" -eq 0 ] || { echo 'CRL_INSTALL_ARGUMENTS_INVALID' >&2; exit 2; }
[ "$(id -u)" -eq 0 ] || { echo 'CRL_INSTALL_REQUIRES_ROOT' >&2; exit 2; }
source_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -P)
id imbasac-ca >/dev/null 2>&1 || { echo 'CRL_INSTALL_SIGNER_FIRST' >&2; exit 2; }
[ -f /usr/local/lib/imbasac-ca/pki.py ] || { echo 'CRL_INSTALL_SIGNER_FIRST' >&2; exit 2; }
for target in /usr/local/lib/imbasac-ca/crl_jobs.py /usr/local/lib/imbasac-ca/publish_crl.py /usr/local/lib/imbasac-ca/crl-refresh /usr/local/lib/imbasac-ca/crl-publish /etc/imbasac/crl-publication.json /etc/systemd/system/imbasac-device-crl-refresh.service /etc/systemd/system/imbasac-device-crl-publish.service /etc/systemd/system/imbasac-device-crl-publish.timer; do
  [ ! -e "$target" ] && [ ! -L "$target" ] || { echo 'CRL_INSTALL_EXISTING_FILE_REVIEW_REQUIRED' >&2; exit 2; }
done
for directory in /usr/local/lib/imbasac-ca /etc/imbasac /var/lib/imbasac-ca/crl-spool; do
  [ ! -L "$directory" ] || { echo 'CRL_INSTALL_SYMLINK_FORBIDDEN' >&2; exit 2; }
done
if [ ! -d /var/lib/imbasac-ca/crl-spool ]; then install -d -o imbasac-ca -g imbasac-ca -m 0700 /var/lib/imbasac-ca/crl-spool; fi
for module in crl_jobs.py publish_crl.py; do install -o root -g root -m 0644 "$source_dir/$module" "/usr/local/lib/imbasac-ca/$module"; done
for executable in crl-refresh crl-publish; do install -o root -g root -m 0755 "$source_dir/$executable" "/usr/local/lib/imbasac-ca/$executable"; done
install -o root -g imbasac-ca -m 0640 "$source_dir/crl-publication.example.json" /etc/imbasac/crl-publication.json
for unit in imbasac-device-crl-refresh.service imbasac-device-crl-publish.service imbasac-device-crl-publish.timer; do
  install -o root -g root -m 0644 "$source_dir/systemd/$unit" "/etc/systemd/system/$unit"
done
echo 'CRL maintenance files installed. Services and timer remain stopped/disabled; first bundle and trust validation required.'
