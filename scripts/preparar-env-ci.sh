#!/usr/bin/env bash
# Gera o .env do teste ponta a ponta a partir do .env.example, com o webhook ligado e segredos descartáveis.
set -euo pipefail

cd "$(dirname "$0")/.."
cp .env.example .env

definir() {
  sed -i "s|^$1=.*|$1=$2|" .env
}

definir WHATSAPP_ENABLED true
definir WHATSAPP_VERIFY_TOKEN "$(openssl rand -hex 32)"
definir WHATSAPP_APP_SECRET "$(openssl rand -hex 32)"
definir WHATSAPP_PHONE_NUMBER_ID 123456789012345
echo ".env do CI gerado"
