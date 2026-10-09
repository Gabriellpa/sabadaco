# MongoDB do sabadaco

Guarda as playlists. Roda no namespace `prd`, ao lado do bot, com PVC `local-path` e backup diário.

| Recurso | O quê |
|---|---|
| `sabadaco-mongo` (Deployment + Service) | `mongo:8.2.12`, cache do WiredTiger em 256MB, limite 768Mi |
| `sabadaco-mongo-data` (PVC 2Gi) | dados |
| `sabadaco-mongo-init` (ConfigMap) | cria o usuário `sabadaco` (readWrite só no banco `sabadaco`) na primeira subida |
| `sabadaco-mongo-backup` (CronJob 03:15 UTC) | `mongodump --gzip` no PVC `sabadaco-mongo-backups`, 7 dias; cópia externa opcional via Secret `backup-rclone` |
| `sabadaco-mongo` (Secret, **não versionado**) | senhas do root e do usuário do bot, criado por `create-secret.sh` |
| `sabadaco-bot-mongo` (Secret, **não versionado**) | só `MONGODB_URI`, para o bot; criado pelo mesmo script |

A 8.0 não serve: ela se recusa a subir em kernel Linux 6.19 ou mais novo, e a VM roda 7.0.

## Primeira subida

Na VM, a partir da raiz do repo:

```bash
./deploy/mongo/create-secret.sh
kubectl apply -k deploy/mongo
kubectl -n prd rollout status deploy/sabadaco-mongo
```

Depois, no app `sabadaco-bot` (projeto `sbc`, ambiente prd), com uma imagem que tenha a persistência:

- variável `STORAGE_TYPE=mongo`
- `MONGODB_URI` vinda do Secret `sabadaco-bot-mongo` (`envFrom` ou `secretKeyRef`). Se a UI ainda não souber
  referenciar um Secret existente, copie o valor com
  `kubectl -n prd get secret sabadaco-bot-mongo -o jsonpath='{.data.MONGODB_URI}' | base64 -d` para o segredo do app.

e reimplante o bot. No log aparece a conexão com o Mongo; `/playlist create` seguido de um restart do pod deve manter a playlist.

## Backup manual e restore

```bash
# backup agora
kubectl -n prd create job sabadaco-mongo-backup-manual --from=cronjob/sabadaco-mongo-backup
kubectl -n prd logs job/sabadaco-mongo-backup-manual -c dump

# restore de um arquivo do PVC de backups (substitui as coleções do banco sabadaco)
kubectl -n prd run mongo-restore --rm -it --restart=Never --image=mongo:8.2.12 \
  --overrides='{"spec":{"volumes":[{"name":"b","persistentVolumeClaim":{"claimName":"sabadaco-mongo-backups"}}],"containers":[{"name":"mongo-restore","image":"mongo:8.2.12","stdin":true,"tty":true,"command":["bash"],"envFrom":[{"secretRef":{"name":"sabadaco-mongo"}}],"volumeMounts":[{"name":"b","mountPath":"/backup"}]}]}}'
# dentro do pod:
ls /backup
mongorestore --host sabadaco-mongo -u "$MONGO_INITDB_ROOT_USERNAME" -p "$MONGO_INITDB_ROOT_PASSWORD" \
  --authenticationDatabase admin --drop --gzip --archive=/backup/mongo-AAAAMMDD-HHMMSS.archive.gz
```

## Cuidados

- O `local-path` apaga o volume junto com o PVC. Não rode `kubectl delete -k deploy/mongo` sem um dump recente fora da VM.
- As senhas do Secret só valem na primeira subida (volume vazio). Trocar o Secret depois não troca a senha no banco.
