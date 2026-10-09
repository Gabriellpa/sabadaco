#!/usr/bin/env bash
# Cria o Secret sabadaco-mongo (senhas aleatórias) no ns prd. Rode na VM, onde está o kubectl.
# Não sobrescreve um Secret existente: as senhas valem para sempre depois que o volume é criado.
#
# A URI que o bot usa (MONGODB_URI) é gravada em deploy/mongo/.mongodb-uri (fora do git,
# permissão 600) e não aparece no terminal. Copie o valor para o Secret do app pela UI.
set -euo pipefail

NS=prd
SECRET=sabadaco-mongo
HERE="$(cd "$(dirname "$0")" && pwd)"
URI_FILE="$HERE/.mongodb-uri"

if kubectl -n "$NS" get secret "$SECRET" >/dev/null 2>&1; then
  echo "Secret $NS/$SECRET já existe; nada a fazer."
  exit 0
fi

# hex: sem caracteres que precisem de escape na URI
root_password="$(openssl rand -hex 24)"
app_password="$(openssl rand -hex 24)"

kubectl -n "$NS" create secret generic "$SECRET" \
  --from-literal=MONGO_INITDB_ROOT_USERNAME=root \
  --from-literal=MONGO_INITDB_ROOT_PASSWORD="$root_password" \
  --from-literal=MONGO_APP_USER=sabadaco \
  --from-literal=MONGO_APP_PASSWORD="$app_password"

umask 077
printf 'mongodb://sabadaco:%s@sabadaco-mongo.%s.svc:27017/sabadaco\n' "$app_password" "$NS" > "$URI_FILE"
echo "Secret $NS/$SECRET criado. MONGODB_URI do bot gravada em $URI_FILE"
