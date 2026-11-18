#!/usr/bin/env bash
# Read-only, targeted summary of the three known enabled sites. Not a full Nginx parser.
awk '
FNR == 1 {print "ARCHIVO: " FILENAME}
{
    line=$0; sub(/#.*/, "", line)
    if (line ~ /^[[:space:]]*(server|listen|server_name|location|alias|allow|deny|limit_except|auth_request|auth_basic|internal|satisfy|proxy_pass|try_files|include|ssl_protocols)[[:space:];{]/) {
        short=line; sub(/[;{].*$/, "", short); print FNR ": " short
    }
    if (line ~ /^[[:space:]]*}/) print FNR ": }"
    if (match(line, /return[[:space:]]+[0-9][0-9][0-9]/)) print FNR ": " substr(line,RSTART,RLENGTH)
}
' /etc/nginx/sites-enabled/imbasac.cloud /etc/nginx/sites-enabled/php-internal-localhost.conf /etc/nginx/sites-enabled/pos |
sed -E 's#(https?://)[^/@[:space:]]+@#\1[CREDENCIALES]@#g; s#\?[^[:space:];]*#?[PARAMETROS]#g'
