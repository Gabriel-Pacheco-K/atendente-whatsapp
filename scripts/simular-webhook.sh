#!/usr/bin/env bash
# Manda ao webhook uma mensagem de texto como a Meta mandaria: payload da Cloud API, horário de agora e assinatura
# HMAC-SHA256 com o WHATSAPP_APP_SECRET do .env (ou do ambiente). Imprime o status HTTP e o wamid usado.
#   scripts/simular-webhook.sh "oi"                         # wamid novo a cada chamada
#   scripts/simular-webhook.sh --wamid wamid.ci.123 "oi"    # repete um wamid, para conferir a idempotência
#   scripts/simular-webhook.sh --assinatura-errada "oi"     # assina com outro segredo: tem de dar 401
set -euo pipefail

cd "$(dirname "$0")/.."
if [[ -f .env ]]; then
  set -a
  . ./.env
  set +a
fi

atendente="${ATENDENTE_URL:-http://127.0.0.1:8080}"
wa_id="${WA_ID:-5548999990000}"
segredo="${WHATSAPP_APP_SECRET:?defina WHATSAPP_APP_SECRET no .env}"
numero="${WHATSAPP_PHONE_NUMBER_ID:?defina WHATSAPP_PHONE_NUMBER_ID no .env}"
wamid="wamid.ci.$(date +%s%N)"

while [[ $# -gt 1 ]]; do
  case "$1" in
    --wamid) wamid=$2; shift 2 ;;
    --assinatura-errada) segredo="outro-segredo-qualquer"; shift ;;
    *) echo "Opção desconhecida: $1" >&2; exit 2 ;;
  esac
done
texto="${1:?informe o texto da mensagem}"

# O corpo vai para um arquivo: a assinatura precisa ser dos mesmos bytes que o curl envia.
corpo=$(mktemp)
trap 'rm -f "$corpo"' EXIT
jq -cjn --arg numero "$numero" --arg wa_id "$wa_id" --arg wamid "$wamid" --arg agora "$(date +%s)" \
  --arg texto "$texto" '{
    object: "whatsapp_business_account",
    entry: [{id: "WABA_ID", changes: [{field: "messages", value: {
      messaging_product: "whatsapp",
      metadata: {display_phone_number: "15550000000", phone_number_id: $numero},
      contacts: [{profile: {name: "Cliente Teste"}, wa_id: $wa_id}],
      messages: [{from: $wa_id, id: $wamid, timestamp: $agora, type: "text", text: {body: $texto}}]
    }}]}]
  }' >"$corpo"
assinatura=$(openssl dgst -sha256 -hmac "$segredo" "$corpo" | sed 's/^.*= //')

status=$(curl -sS -o /dev/null -w '%{http_code}' -X POST "$atendente/webhook/whatsapp" \
  -H 'Content-Type: application/json' -H "X-Hub-Signature-256: sha256=$assinatura" --data-binary @"$corpo")
echo "$status $wamid"
