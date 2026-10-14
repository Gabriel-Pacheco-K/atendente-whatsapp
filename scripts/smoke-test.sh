#!/usr/bin/env bash
# Teste ponta a ponta contra o ambiente do docker compose: o atendente em 127.0.0.1:8080 e o agendamento-api real
# em 127.0.0.1:8081. Marca horários de verdade, então rode com o ambiente recém-criado:
#   scripts/preparar-env-ci.sh && docker compose down -v && docker compose up -d --build && scripts/smoke-test.sh
# O passo do webhook usa os segredos do .env (ou do ambiente) e lê o log do atendente (LOGS_DO_ATENDENTE, um comando).
set -euo pipefail

RAIZ="$(cd "$(dirname "$0")/.." && pwd)"
ATENDENTE="${ATENDENTE_URL:-http://127.0.0.1:8080}"
AGENDA="${AGENDAMENTO_API_URL:-http://127.0.0.1:8081}"
LOGS_DO_ATENDENTE="${LOGS_DO_ATENDENTE:-docker compose -f '$RAIZ/docker-compose.yml' logs --no-color atendente}"
BUSINESS_YML="$RAIZ/src/main/resources/business.yml"
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

# Quantos turnos do WhatsApp o atendente já registrou: prova que a mensagem foi processada, não só respondida com 200.
turnos_do_whatsapp() {
  bash -c "$LOGS_DO_ATENDENTE" | grep -c 'turno canal=WHATSAPP' || true
}

esperar_turnos_do_whatsapp() {
  local tentativas=20
  until [[ $(turnos_do_whatsapp) -ge $1 ]]; do
    (( --tentativas > 0 )) || falhar "a mensagem do webhook não virou um turno no log do atendente"
    sleep 0.5
  done
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
esperar "$(dizer "$cliente" "Ana Souza")" "Confirma o agendamento?"
esperar "$(dizer "$cliente" "sim")" "Agendado, Ana!"
[[ $(status_na_agenda "Ana Souza" 10:00) == SCHEDULED ]] || falhar "o agendamento não está SCHEDULED na agenda"

passo "Cancelamento pelo chat"
esperar "$(dizer "$cliente" "cancelar")" "Você quer cancelar"
esperar "$(dizer "$cliente" "sim")" "Pronto, cancelei."
[[ $(status_na_agenda "Ana Souza" 10:00) == CANCELLED ]] || falhar "o agendamento não está CANCELLED na agenda"

passo "Corrida: duas sessões confirmam o mesmo horário, às 11h"
bento=$(nova_sessao "+55 48 99999-0002")
carla=$(nova_sessao "+55 48 99999-0003")
for sessao in "$bento" "$carla"; do
  esperar "$(dizer "$sessao" "quero cortar dia $dia_e_mes às 11h com o joão")" "Boa notícia"
done
esperar "$(dizer "$bento" "Bento Ramos")" "Confirma o agendamento?"
esperar "$(dizer "$carla" "Carla Mendes")" "Confirma o agendamento?"
esperar "$(dizer "$bento" "sim")" "Agendado, Bento!"
esperar "$(dizer "$carla" "sim")" "Puxa, alguém acabou de reservar esse horário."
[[ $(status_na_agenda "Bento Ramos" 11:00) == SCHEDULED ]] || falhar "o horário das 11h não ficou com quem confirmou primeiro"
[[ -z $(status_na_agenda "Carla Mendes" 11:00) ]] || falhar "o horário das 11h foi marcado duas vezes"

passo "Webhook do WhatsApp: verificação, mensagem assinada, mesmo wamid de novo e assinatura errada"
if [[ -f "$RAIZ/.env" ]]; then
  set -a
  . "$RAIZ/.env"
  set +a
fi
[[ -n ${WHATSAPP_VERIFY_TOKEN:-} ]] || falhar "faltam os segredos do webhook no .env (gere com scripts/preparar-env-ci.sh)"
desafio=$(curl -fsS -G "$ATENDENTE/webhook/whatsapp" --data-urlencode "hub.mode=subscribe" \
  --data-urlencode "hub.verify_token=$WHATSAPP_VERIFY_TOKEN" --data-urlencode "hub.challenge=desafio-123")
[[ $desafio == desafio-123 ]] || falhar "a verificação do webhook não devolveu o challenge: $desafio"
antes=$(turnos_do_whatsapp)
read -r status wamid <<<"$("$RAIZ/scripts/simular-webhook.sh" "oi")"
[[ $status == 200 ]] || falhar "o webhook assinado respondeu $status"
esperar_turnos_do_whatsapp $((antes + 1))
read -r status _ <<<"$("$RAIZ/scripts/simular-webhook.sh" --wamid "$wamid" "oi")"
[[ $status == 200 ]] || falhar "o reenvio do mesmo wamid respondeu $status"
read -r status _ <<<"$("$RAIZ/scripts/simular-webhook.sh" --assinatura-errada "oi")"
[[ $status == 401 ]] || falhar "a assinatura errada respondeu $status, e não 401"
[[ $(turnos_do_whatsapp) == $((antes + 1)) ]] || falhar "o reenvio ou a mensagem forjada viraram um turno"

echo "Ponta a ponta OK"
