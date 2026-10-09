#!/usr/bin/env bash
# Cria os Secrets do Mongo do sabadaco no ns prd, com senhas aleatórias. Rode na VM, onde está o kubectl.
# Não sobrescreve Secrets existentes: as senhas valem para sempre depois que o volume é criado.
#
#   sabadaco-mongo      usuário root e usuário do bot (lidos pelo Mongo e pelo CronJob de backup)
#   sabadaco-bot-mongo  só MONGODB_URI, para o bot ler; não carrega a senha do root
#
# Nenhuma senha aparece no terminal nem vai para arquivo.
set -euo pipefail

NS=prd
SERVER_SECRET=sabadaco-mongo
APP_SECRET=sabadaco-bot-mongo

if kubectl -n "$NS" get secret "$SERVER_SECRET" >/dev/null 2>&1; then
  echo "Secret $NS/$SERVER_SECRET já existe; nada a fazer."
  exit 0
fi

# hex: sem caracteres que precisem de escape na URI
root_password="$(openssl rand -hex 24)"
app_password="$(openssl rand -hex 24)"

kubectl -n "$NS" create secret generic "$SERVER_SECRET" \
  --from-literal=MONGO_INITDB_ROOT_USERNAME=root \
  --from-literal=MONGO_INITDB_ROOT_PASSWORD="$root_password" \
  --from-literal=MONGO_APP_USER=sabadaco \
  --from-literal=MONGO_APP_PASSWORD="$app_password"
kubectl -n "$NS" label secret "$SERVER_SECRET" app=sabadaco-mongo >/dev/null

kubectl -n "$NS" create secret generic "$APP_SECRET" \
  --from-literal=MONGODB_URI="mongodb://sabadaco:${app_password}@sabadaco-mongo.${NS}.svc:27017/sabadaco"
kubectl -n "$NS" label secret "$APP_SECRET" app=sabadaco-bot >/dev/null

echo "Secrets $NS/$SERVER_SECRET e $NS/$APP_SECRET criados."
