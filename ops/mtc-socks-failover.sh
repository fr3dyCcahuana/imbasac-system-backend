#!/usr/bin/env bash

set -u

SOCKS_HOST="127.0.0.1"
SOCKS_PORT="1080"
SSH_USER="mtcproxy"
SSH_KEY="/opt/mtc-proxy/mtc_proxy_key"
HEALTH_URL="https://licencias.mtc.gob.pe/"
HEALTH_INTERVAL=30
HEALTH_FAILURE_LIMIT=2
SSH_PID=""

# Se conserva el orden de los puentes del servidor.
BRIDGES=(
  "100.95.119.35|desktop-hsuc20i"
  "100.127.129.31|desktop-405ul4q"
  "100.102.37.1|desktop-iik5jon"
  "100.72.157.59|ventas5"
  "100.119.177.60|venta3"
  "100.87.113.123|desktop-eiu4nki"
  "100.64.8.24|servidor"
)

log() {
  echo "$(date '+%Y-%m-%d %H:%M:%S') [mtc-socks-failover] $*"
}

is_port_busy() {
  ss -H -ltn "sport = :${SOCKS_PORT}" | grep -q .
}

test_bridge_ssh() {
  local ip="$1"
  timeout 12 ssh \
    -i "${SSH_KEY}" \
    -o BatchMode=yes \
    -o ConnectTimeout=8 \
    -o StrictHostKeyChecking=accept-new \
    -o PreferredAuthentications=publickey \
    "${SSH_USER}@${ip}" "echo OK" >/dev/null 2>&1
}

test_proxy_health() {
  local status
  # GET por el mismo SOCKS y con resolucion en el puente, como Chromium.
  # curl tambien debe rechazar respuestas HTTP 403/500, no solo fallos de red.
  status=$(curl \
    --socks5-hostname "${SOCKS_HOST}:${SOCKS_PORT}" \
    --noproxy "" \
    --fail --location --silent --show-error \
    --connect-timeout 5 --max-time 10 \
    --output /dev/null --write-out '%{http_code}' \
    "${HEALTH_URL}" 2>/dev/null) || return 1
  [[ "${status}" == "200" ]]
}

stop_tunnel() {
  if [[ -n "${SSH_PID}" ]]; then
    kill "${SSH_PID}" >/dev/null 2>&1 || true
    wait "${SSH_PID}" >/dev/null 2>&1 || true
    SSH_PID=""
  fi
}

open_tunnel() {
  local ip="$1"
  local name="$2"

  ssh -N \
    -i "${SSH_KEY}" \
    -D "${SOCKS_HOST}:${SOCKS_PORT}" \
    -o BatchMode=yes \
    -o ConnectTimeout=8 \
    -o ExitOnForwardFailure=yes \
    -o ServerAliveInterval=30 \
    -o ServerAliveCountMax=3 \
    -o StrictHostKeyChecking=accept-new \
    -o PreferredAuthentications=publickey \
    "${SSH_USER}@${ip}" &
  SSH_PID=$!
  sleep 3

  if ! kill -0 "${SSH_PID}" >/dev/null 2>&1 || ! test_proxy_health; then
    log "Puente ${name} (${ip}): el tunel no completo el acceso al MTC."
    stop_tunnel
    return 1
  fi
  log "Puente ${name} (${ip}): MTC disponible por ${SOCKS_HOST}:${SOCKS_PORT}."
}

monitor_tunnel() {
  local name="$1"
  local failures=0

  while kill -0 "${SSH_PID}" >/dev/null 2>&1; do
    sleep "${HEALTH_INTERVAL}"
    if ! kill -0 "${SSH_PID}" >/dev/null 2>&1; then
      break
    fi
    if test_proxy_health; then
      if (( failures > 0 )); then
        log "Puente ${name}: acceso al MTC recuperado."
      fi
      failures=0
    else
      failures=$((failures + 1))
      log "Puente ${name}: fallo de acceso al MTC (${failures}/${HEALTH_FAILURE_LIMIT})."
      if (( failures >= HEALTH_FAILURE_LIMIT )); then
        break
      fi
    fi
  done

  log "Puente ${name}: cerrando tunel y probando el siguiente equipo."
  stop_tunnel
}

check_bridges() {
  local item ip name
  local healthy=0
  # Puerto separado: no cierra ni utiliza el tunel de produccion en 1080.
  SOCKS_PORT="11080"
  if is_port_busy; then
    log "El puerto de prueba ${SOCKS_PORT} esta ocupado; no se modifico el tunel de produccion."
    return 1
  fi

  for item in "${BRIDGES[@]}"; do
    ip="${item%%|*}"
    name="${item##*|}"
    log "Comprobando ${name} (${ip})..."
    if ! test_bridge_ssh "${ip}"; then
      log "NO DISPONIBLE: ${name}; revisar encendido, Tailscale, SSH y llave autorizada."
    elif open_tunnel "${ip}" "${name}"; then
      healthy=$((healthy + 1))
      log "APTO: ${name}; autenticacion SSH y acceso al MTC correctos."
      stop_tunnel
    else
      log "NO APTO: ${name}; SSH responde pero no completa el acceso al MTC."
    fi
  done
  log "Puentes aptos: ${healthy}/${#BRIDGES[@]}. Para tolerar un equipo apagado se necesitan al menos dos."
  (( healthy >= 2 ))
}

run_failover() {
  local item ip name
  log "Iniciando failover SOCKS para MTC/SUNAT/DNI con supervision del acceso al MTC..."
  while true; do
    if is_port_busy; then
      log "El puerto ${SOCKS_HOST}:${SOCKS_PORT} esta ocupado. Esperando 10 segundos..."
      sleep 10
      continue
    fi
    for item in "${BRIDGES[@]}"; do
      ip="${item%%|*}"
      name="${item##*|}"
      log "Probando puente ${name} (${ip})..."
      if ! test_bridge_ssh "${ip}"; then
        log "Puente ${name} no disponible por SSH. Probando siguiente..."
        continue
      fi
      if open_tunnel "${ip}" "${name}"; then
        monitor_tunnel "${name}"
      fi
    done
    log "Fin de la ronda. Reintentando en 15 segundos..."
    sleep 15
  done
}

main() {
  trap stop_tunnel EXIT
  trap 'exit 0' INT TERM
  case "${1:-}" in
    --check-bridges) check_bridges ;;
    "") run_failover ;;
    *) echo "Uso: $0 [--check-bridges]" >&2; return 2 ;;
  esac
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  main "$@"
fi
