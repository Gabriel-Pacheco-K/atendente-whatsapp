#!/bin/sh
# Cadastra no agendamento-api os profissionais, o expediente e os serviços da Barbearia Navalha de Ouro.
# Pode rodar quantas vezes for preciso: cada item só é criado se ainda não existir um com o mesmo nome, e um
# profissional que ficou sem expediente (seed interrompido no meio) é completado.
# Usa só sh e curl. Qualquer resposta que não seja 2xx interrompe o seed, e o atendente não sobe.
set -eu

API="${API:-http://localhost:8081}"
created=0

fail() {
  echo "Seed: $1" >&2
  exit 1
}

post() {
  curl -fsS -X POST -H 'Content-Type: application/json' -d "$2" "$API$1"
}

# Expediente de terça a sexta em dois turnos, mais o sábado das 08:00 às 14:00.
periods() {
  morning_start=$1 morning_end=$2 afternoon_start=$3 afternoon_end=$4
  list=""
  for day in TUESDAY WEDNESDAY THURSDAY FRIDAY; do
    list="$list{\"dayOfWeek\":\"$day\",\"start\":\"$morning_start\",\"end\":\"$morning_end\"},"
    list="$list{\"dayOfWeek\":\"$day\",\"start\":\"$afternoon_start\",\"end\":\"$afternoon_end\"},"
  done
  echo "{\"periods\":[$list{\"dayOfWeek\":\"SATURDAY\",\"start\":\"08:00\",\"end\":\"14:00\"}]}"
}

ensure_professional() {
  name=$1 working_hours=$2
  ids=$(printf '%s' "$professionals" | grep -o "\"id\":[0-9]*,\"name\":\"$name\"" | sed 's/^"id":\([0-9]*\),.*/\1/' || true)
  count=$(printf '%s' "$ids" | grep -c . || true)
  case "$count" in
    0)
      id=$(post /api/professionals "{\"name\":\"$name\",\"specialty\":\"Barbeiro\"}" \
        | sed -n 's/^{"id":\([0-9]*\).*/\1/p')
      [ -n "$id" ] || fail "não consegui ler o id do profissional $name"
      echo "Profissional $name cadastrado (#$id)"
      created=$((created + 1))
      ;;
    1)
      id=$ids
      ;;
    *)
      fail "há $count profissionais chamados $name no agendamento-api; apague os repetidos ou recomece do zero com 'docker compose down -v'"
      ;;
  esac
  current=$(curl -fsS "$API/api/professionals/$id" | grep -o '"dayOfWeek"' | wc -l)
  if [ "$current" -eq 0 ]; then
    curl -fsS -X PUT -H 'Content-Type: application/json' -d "$working_hours" \
      "$API/api/professionals/$id/working-hours" > /dev/null
    echo "Expediente de $name cadastrado"
    created=$((created + 1))
  fi
}

ensure_service() {
  name=$1 duration=$2 price=$3
  if printf '%s' "$services" | grep -q "\"name\":\"$name\""; then
    return
  fi
  post /api/services "{\"name\":\"$name\",\"durationMinutes\":$duration,\"price\":$price}" > /dev/null
  echo "Serviço $name cadastrado"
  created=$((created + 1))
}

professionals=$(curl -fsS "$API/api/professionals")
services=$(curl -fsS "$API/api/services")

ensure_professional "João Batista" "$(periods 09:00 12:00 13:00 19:00)"
ensure_professional "Pedro Henrique" "$(periods 10:00 13:00 14:00 19:00)"

ensure_service "Corte masculino" 30 45.00
ensure_service "Barba" 30 35.00
ensure_service "Corte + barba" 60 70.00
ensure_service "Sobrancelha" 15 15.00

if [ "$created" -eq 0 ]; then
  echo "Catálogo já existe"
fi
