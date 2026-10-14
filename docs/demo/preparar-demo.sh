#!/usr/bin/env bash
# Prepara a cena do horário ocupado da demo: marca direto no agendamento-api um Corte masculino com o Pedro
# na próxima sexta (ou hoje, se for sexta) às 10:00, para o "Cliente Exemplo". Rode com o ambiente recém-criado
# (docker compose down -v && docker compose up --build -d). Rodar de novo não duplica: o 409 conta como pronto.
set -euo pipefail

AGENDA="${AGENDAMENTO_API_URL:-http://127.0.0.1:8081}"
sexta=$(TZ=America/Sao_Paulo date -d friday +%F)

id_por_nome() {
  curl -fsS "$AGENDA/api/$1" | jq -er --arg nome "$2" '.[] | select(.name == $nome) | .id'
}

falhar() {
  echo "$1" >&2
  exit 1
}

pedro=$(id_por_nome professionals "Pedro Henrique") || falhar "Pedro Henrique não está no catálogo; o seed rodou?"
corte=$(id_por_nome services "Corte masculino") || falhar "Corte masculino não está no catálogo; o seed rodou?"

corpo=$(jq -n --argjson profissional "$pedro" --argjson servico "$corte" --arg inicio "${sexta}T10:00:00" \
  '{professionalId: $profissional, serviceId: $servico, startAt: $inicio,
    customerName: "Cliente Exemplo", customerPhone: "+55 48 90000-0001"}')
status=$(curl -sS -o /dev/null -w '%{http_code}' -X POST "$AGENDA/api/appointments" \
  -H 'Content-Type: application/json' -d "$corpo")

case "$status" in
  201) echo "Sexta $sexta às 10:00 com o Pedro reservada para o Cliente Exemplo" ;;
  409) echo "Sexta $sexta às 10:00 com o Pedro já estava ocupada" ;;
  *) falhar "O agendamento-api respondeu $status ao preparar a demo" ;;
esac
