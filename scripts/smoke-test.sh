#!/usr/bin/env bash
# Teste ponta a ponta contra o ambiente do docker compose: o atendente em 127.0.0.1:8080 e o agendamento-api real
# em 127.0.0.1:8081. Marca horários de verdade, então rode com o ambiente recém-criado:
#   docker compose down -v && docker compose up -d --build && scripts/smoke-test.sh
set -euo pipefail

ATENDENTE="${ATENDENTE_URL:-http://127.0.0.1:8080}"
AGENDA="${AGENDAMENTO_API_URL:-http://127.0.0.1:8081}"
BUSINESS_YML="$(dirname "$0")/../src/main/resources/business.yml"
FUSO=America/Sao_Paulo

falhar() {
  echo "FALHOU: $*" >&2
  exit 1
}

passo() {
  echo "==> $*"
}

nova_sessao() {
  curl -fsS -X POST "$ATENDENTE/api/chat/sessions" -H 'Content-Type: application/json' \
    -d "{\"phone\":\"$1\"}" | jq -r .sessionId
}

# Manda uma mensagem pelo chat e devolve os textos das respostas.
dizer() {
  local sessao=$1 texto=$2 corpo
  corpo=$(jq -n --arg id "$(cat /proc/sys/kernel/random/uuid)" --arg texto "$texto" \
    '{clientMessageId: $id, text: $texto}')
  curl -fsS -X POST "$ATENDENTE/api/chat/sessions/$sessao/messages" -H 'Content-Type: application/json' \
    -d "$corpo" | jq -r '.replies[].text'
}

esperar() {
  grep -qF -- "$2" <<<"$1" || falhar "esperava \"$2\" na resposta: $1"
}

# O status, na agenda real do João, do agendamento de um cliente num horário do dia do teste (vazio se não houver).
status_na_agenda() {
  curl -fsS "$AGENDA/api/appointments?professionalId=1&date=$dia" \
    | jq -r --arg nome "$1" --arg inicio "${dia}T$2:00" \
      '.[] | select(.customerName == $nome and .startAt == $inicio) | .status'
}

passo "Catálogo do seed: 2 profissionais com 9 períodos de expediente cada e 4 serviços"
profissionais=$(curl -fsS "$AGENDA/api/professionals")
[[ $(jq 'length' <<<"$profissionais") == 2 ]] || falhar "esperava 2 profissionais: $profissionais"
[[ $(jq -c '[.[].workingHours | length]' <<<"$profissionais") == "[9,9]" ]] || falhar "expediente incompleto: $profissionais"
[[ $(curl -fsS "$AGENDA/api/services" | jq 'length') == 4 ]] || falhar "esperava 4 serviços"

# A próxima quarta no fuso da barbearia (o runner do CI roda em UTC), pulando os dias fechados do business.yml.
hoje=$(TZ=$FUSO date +%F)
dia=$(TZ=$FUSO date -d 'next wednesday' +%F)
while grep -q "date: \"$dia\"" "$BUSINESS_YML"; do
  dia=$(date -d "$dia + 7 days" +%F)
done
[[ ! "$dia" > "$(date -d "$hoje + 30 days" +%F)" ]] || falhar "a quarta $dia passa dos 30 dias que o atendente oferece"
dia_e_mes=$(date -d "$dia" +%d/%m)

passo "Agendamento de verdade na quarta $dia_e_mes às 10h com o João"
cliente=$(nova_sessao "+55 48 99999-0001")
esperar "$(dizer "$cliente" "quero cortar dia $dia_e_mes às 10h com o joão")" "Boa notícia"
esperar "$(dizer "$cliente" "Teste Integrado")" "Confirma o agendamento?"
esperar "$(dizer "$cliente" "sim")" "Agendado, Teste!"
[[ $(status_na_agenda "Teste Integrado" 10:00) == SCHEDULED ]] || falhar "o agendamento não está SCHEDULED na agenda"

passo "Cancelamento pelo chat"
esperar "$(dizer "$cliente" "cancelar")" "Você quer cancelar"
esperar "$(dizer "$cliente" "sim")" "Pronto, cancelei."
[[ $(status_na_agenda "Teste Integrado" 10:00) == CANCELLED ]] || falhar "o agendamento não está CANCELLED na agenda"

passo "Corrida: duas sessões confirmam o mesmo horário, às 11h"
bento=$(nova_sessao "+55 48 99999-0002")
carla=$(nova_sessao "+55 48 99999-0003")
for sessao in "$bento" "$carla"; do
  esperar "$(dizer "$sessao" "quero cortar dia $dia_e_mes às 11h com o joão")" "Boa notícia"
done
esperar "$(dizer "$bento" "Bento Corrida")" "Confirma o agendamento?"
esperar "$(dizer "$carla" "Carla Corrida")" "Confirma o agendamento?"
esperar "$(dizer "$bento" "sim")" "Agendado, Bento!"
esperar "$(dizer "$carla" "sim")" "Puxa, alguém acabou de reservar esse horário."
[[ $(status_na_agenda "Bento Corrida" 11:00) == SCHEDULED ]] || falhar "o horário das 11h não ficou com quem confirmou primeiro"
[[ -z $(status_na_agenda "Carla Corrida" 11:00) ]] || falhar "o horário das 11h foi marcado duas vezes"

echo "Ponta a ponta OK"
