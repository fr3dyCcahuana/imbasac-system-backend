#!/usr/bin/env bash
# Read-only summary, never reloads Nginx. Not a complete Nginx parser or proof of isolation.
# Whitelist routing directives; omit header values and redact URL credentials/query arguments.
nginx -T 2>/dev/null | awk '
/^# configuration file / {print; next}
$1 ~ /^(server|listen|server_name|location|root|alias|proxy_pass|fastcgi_pass|upstream|include|allow|deny|ssl_protocols|ssl_verify_client|ssl_client_certificate|ssl_trusted_certificate|ssl_crl|ssl_ocsp)$/ {
    sub(/[;{].*$/, ""); print
}
' | sed -E 's#(https?://)[^/@[:space:]]+@#\1[CREDENCIALES]@#g; s#\?[^[:space:];]*#?[PARAMETROS]#g'
