#!/usr/bin/env bash
# Espera um /actuator/health responder UP. Uso: scripts/aguardar-saude.sh URL SEGUNDOS
set -euo pipefail

url="${1:?informe a URL do health}"
limite="${2:-120}"
inicio=$(date +%s)

until curl -fsS "$url" 2>/dev/null | grep -q '"status":"UP"'; do
  if (( $(date +%s) - inicio >= limite )); then
    echo "Nada de UP em $url depois de ${limite}s" >&2
    exit 1
  fi
  sleep 2
done
echo "$url está UP"
