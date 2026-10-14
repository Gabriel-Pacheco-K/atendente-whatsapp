#!/usr/bin/env bash
# Mostra a agenda do agendamento-api atualizada a cada segundo: uma linha por agendamento, de todos os
# profissionais, nos dias pedidos. Deixe aberto ao lado do chat para ver cada "sim" chegar na agenda.
#   scripts/agenda-ao-vivo.sh                          # amanhã e a próxima sexta
#   scripts/agenda-ao-vivo.sh 2026-10-15 2026-10-16
# Precisa de curl, jq e watch.
set -euo pipefail

AGENDA="${AGENDAMENTO_API_URL:-http://127.0.0.1:8081}"
FUSO=America/Sao_Paulo
DIAS_DA_SEMANA=(segunda-feira terça-feira quarta-feira quinta-feira sexta-feira sábado domingo)

if [[ ${1:-} != --uma-vez ]]; then
  if [[ $# -eq 0 ]]; then
    mapfile -t dias < <(printf '%s\n' "$(TZ=$FUSO date -d tomorrow +%F)" "$(TZ=$FUSO date -d friday +%F)" | sort -u)
    set -- "${dias[@]}"
  fi
  exec watch -n1 -t -x "$0" --uma-vez "$@"
fi
shift

echo "Agenda do agendamento-api ($AGENDA), $(TZ=$FUSO date +%H:%M:%S)"
profissionais=$(curl -fsS "$AGENDA/api/professionals" | jq -r '.[].id')
for dia in "$@"; do
  echo
  echo "${DIAS_DA_SEMANA[$(date -d "$dia" +%u) - 1]}, $(date -d "$dia" +%d/%m)"
  for id in $profissionais; do
    curl -fsS "$AGENDA/api/appointments?professionalId=$id&date=$dia"
  done | jq -rs '
    def coluna(n): if length < n then . + " " * (n - length) else . end;
    (add // []) | sort_by(.startAt) | if length == 0 then "  (nenhum agendamento)" else .[] |
      "  \(.startAt[11:16])  \(.service | coluna(16)) \(.professionalName | coluna(15))" +
      " \(.customerName | coluna(16)) \(.status)"
    end'
done
