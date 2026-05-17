#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8091/api}"
WA_ID="${WA_ID:-SIM_QA_RUC_001}"
RUC="${RUC:-20101036813}"
PRODUCT_1="${PRODUCT_1:-DK151092}"
PRODUCT_2="${PRODUCT_2:-KTR03011468}"

printf '\n== Caso 1: crear proforma con RUC ==\n'
curl -sS -X POST "$BASE_URL/whatsapp/debug/simulate-flow" \
  -H 'Content-Type: application/json' \
  -d "{\n    \"waId\": \"$WA_ID\",\n    \"profileName\": \"Fredy QA\",\n    \"resetBefore\": true,\n    \"messages\": [\"hola\", \"proforma\", \"$PRODUCT_1\", \"2\", \"generar proforma\", \"ruc\", \"$RUC\"]\n  }" | tee /tmp/whatsapp-sim-create.json

printf '\n\n== Caso 2: actualizar la misma proforma ==\n'
curl -sS -X POST "$BASE_URL/whatsapp/debug/simulate-flow" \
  -H 'Content-Type: application/json' \
  -d "{\n    \"waId\": \"$WA_ID\",\n    \"profileName\": \"Fredy QA\",\n    \"resetBefore\": false,\n    \"messages\": [\"agrega este codigo $PRODUCT_2\", \"2\", \"actualizar\"]\n  }" | tee /tmp/whatsapp-sim-update.json

printf '\n\nRevisa estos campos en la respuesta:\n'
printf ' - finalState debe ser PROFORMA_CREATED\n'
printf ' - lastProformaId debe existir y mantenerse en la segunda prueba\n'
printf ' - el ultimo outboundMessages[].textBody debe mostrar documento y resumen real\n'
