# Sabadaço: roteiro de testes manuais

Roteiro para rodar o bot **no Discord de verdade** e o **painel admin** no navegador, do começo ao fim. Cada teste tem objetivo, pré-condição, passos com o comando exato, resultado esperado e uma caixa para marcar quando passar. No fim há uma tabela-resumo para anotar o resultado de cada um.

Para entender o que cada peça faz, veja [ARQUITETURA.md](ARQUITETURA.md). Para rodar o projeto, veja o [README](../README.md).

**Índice**
1. [Preparação](#1-preparação)
2. [Convenções do roteiro](#2-convenções-do-roteiro)
3. [A. Reprodução básica](#a-reprodução-básica)
4. [B. Painel do player](#b-painel-do-player)
5. [C. Fila](#c-fila)
6. [D. Playlists](#d-playlists)
7. [E. Busca](#e-busca)
8. [F. Regra de prioridade](#f-regra-de-prioridade-avulsa--playlist)
9. [G. Controles](#g-controles)
10. [H. Localização](#h-localização-pt-br)
11. [I. Erros](#i-erros)
12. [J. Painel admin](#j-painel-admin)
13. [K. Métricas](#k-métricas)
14. [L. Docker](#l-docker)
15. [M. Ajuda (`/help`)](#m-ajuda-help)
16. [N. Em desenvolvimento (a confirmar)](#n-em-desenvolvimento-a-confirmar)
17. [Resumo dos resultados](#resumo-dos-resultados)

---

## 1. Preparação

### 1.1 Arquivo `.env`

Na raiz do projeto, copie o exemplo e preencha:

```bash
cp .env.example .env
```

| Variável | O que colocar |
|---|---|
| `DISCORD_BOT_TOKEN` | Token do bot (Discord Developer Portal → sua aplicação → Bot → Reset Token). **Obrigatório.** |
| `DISCORD_DEV_GUILD_ID` | *Opcional, recomendado para testar.* ID do seu servidor de teste: os comandos são registrados só nele e aparecem na hora. Vazio = comandos globais (podem demorar para aparecer). Para copiar o ID: Discord → Configurações → Avançado → **Modo desenvolvedor**; botão direito no ícone do servidor → **Copiar ID do servidor**. |
| `ADMIN_USER` | Usuário do painel (padrão `admin`). |
| `ADMIN_PASSWORD` | Senha do painel. Se ficar vazia, o app gera uma senha aleatória e a mostra no log ao subir (`ADMIN_PASSWORD não definido. Senha temporária do painel...`). Para este roteiro, **defina uma** (assim ela não muda a cada restart). |
| `ADMIN_PORT` | Porta do painel no seu computador (padrão `8080`). |

> Os testes de visibilidade entre servidores (T39) precisam de **um segundo servidor** onde o bot também esteja, e os de usuário (T40) de **uma segunda conta** do Discord (um amigo ou uma conta de teste). Se `DISCORD_DEV_GUILD_ID` estiver preenchido, os comandos só existem naquele servidor; para o T39 deixe-o vazio ou registre em outro servidor depois.

### 1.2 Subir o bot

```bash
docker compose up -d --build
docker compose logs -f
```

Aguarde as linhas do log. **Esperado:**

- O Spring sobe sem erro (sem stack trace em vermelho).
- Aparece a mensagem de registro dos comandos, algo como:
  - `11 comandos registrados no servidor <nome do seu servidor>` (com `DISCORD_DEV_GUILD_ID`), ou
  - `11 comandos registrados globalmente` (sem ele).
- Se você não definiu `ADMIN_PASSWORD`, aparece o aviso com a senha temporária.
- Abra http://localhost:8080 : deve redirecionar para a tela de login do painel.

Se aparecer `Falha ao registrar comandos`, confira o token e se o bot foi convidado com o escopo `applications.commands`.

### 1.3 Convidar o bot

No Developer Portal → sua aplicação → OAuth2 → URL Generator:

- **Scopes:** `bot` e `applications.commands`.
- **Permissões do bot:** `Connect`, `Speak`, `Send Messages` e `Use Application Commands` (o painel do player é uma mensagem, então ele também precisa poder escrever e ver o canal de texto onde você usar os comandos).

Abra a URL gerada e adicione o bot ao seu servidor de teste. O bot deve aparecer na lista de membros (offline até a conexão subir, depois online).

### 1.4 Entrar num canal de voz

Entre num canal de voz do servidor de teste com a **sua conta**. Quase todos os testes de reprodução exigem isso. Use fone para ouvir de verdade (ou confirme só pelo indicador verde de "falando" do bot).

### 1.5 Músicas usadas nos exemplos

| Uso | Valor |
|---|---|
| Busca | `kasino sabadaço gilberto barros` |
| URL 1 | https://www.youtube.com/watch?v=RGqH4mfmtkQ (Kasino – Can't Get Over ft. Gilberto Barros, versão Sabadaço) |
| URL 2 | https://www.youtube.com/watch?v=LCDaw0QmQQc (Kasino no Sabadaço, 07/10/2006). É a mesma que o `/kassino` toca. |

> O apresentador do Sabadaço é o Gilberto Barros.

### 1.6 Se o YouTube não tocar

Se algum teste de reprodução falhar com `This video requires login` ou nada tocar, veja o `.env.example` (`YOUTUBE_OAUTH_ENABLED`, com conta Google descartável) e a seção "Limitações conhecidas" do [ARQUITETURA.md](ARQUITETURA.md#12-limitações-conhecidas). Isso é do YouTube, não dos casos de teste abaixo.

---

## 2. Convenções do roteiro

- Comandos aparecem com o **nome em inglês** e, entre parênteses, o **nome em pt-BR** que o cliente Discord em português mostra. Exemplo: `/play` (`/tocar`). Os dois são o mesmo comando; use o que seu cliente mostrar.
- As **opções** também têm nome em pt-BR. Exemplo: `/play query:...` (`/tocar musica:...`).
- Sempre escolha o item **do autocomplete** nas opções de playlist e de música (o Discord só envia o valor certo assim; digitar o nome exato também funciona).
- `@você` significa a menção da sua conta que o bot mostra nas respostas.
- Respostas **efêmeras** são as que só você vê ("Só visível para você").
- "Painel" = a mensagem do player (capa, barra de progresso e botões). "Admin" = o painel web.

### Tabela de nomes dos comandos

| Inglês | pt-BR | Opções (inglês → pt-BR) |
|---|---|---|
| `/play` | `/tocar` | `query` → `musica` |
| `/search` | `/buscar` | `query` → `busca` |
| `/player` | `/painel` | — |
| `/queue` | `/fila` | — |
| `/skip` | `/pular` | — |
| `/stop` | `/parar` | — |
| `/pause` | `/pausar` | — |
| `/volume` | `/volume` | `level` → `nivel` |
| `/kassino` | `/kassino` | — |
| `/help` | `/ajuda` | `command` → `comando` |
| `/playlist create` | `/playlist criar` | `name` → `nome`, `scope` → `escopo` |
| `/playlist list` | `/playlist listar` | — |
| `/playlist show` | `/playlist ver` | `playlist` |
| `/playlist play` | `/playlist tocar` | `playlist`, `shuffle` → `aleatorio` |
| `/playlist add` | `/playlist adicionar` | `playlist`, `query` → `musica`, `alias` → `apelido` |
| `/playlist add-current` | `/playlist salvar-atual` | `playlist`, `alias` → `apelido` |
| `/playlist alias` | `/playlist apelidar` | `playlist`, `track` → `musica`, `alias` → `apelido` |
| `/playlist remove` | `/playlist remover` | `playlist`, `track` → `musica` |
| `/playlist move` | `/playlist mover` | `from` → `de`, `track` → `musica`, `to` → `para` |
| `/playlist rename` | `/playlist renomear` | `playlist`, `name` → `nome` |
| `/playlist delete` | `/playlist apagar` | `playlist` |

> `/playlist` em si não muda de nome; só os subcomandos. A opção `playlist` também mantém o nome.

### Estado inicial recomendado

Antes de começar o grupo A, o bot não deve ter nada tocando. Se tiver, use `/stop` (`/parar`). Entre os grupos, sempre que um teste pedir "nada tocando", use `/stop`.

---

## A. Reprodução básica

### T01. `/play` por busca

- **Objetivo:** tocar uma música digitando só o nome.
- **Pré-condição:** você está num canal de voz; nada tocando.
- **Passos:**
  1. No canal de texto, digite `/play` (`/tocar`).
  2. Em `query` (`musica`) escreva `kasino sabadaço gilberto barros` e envie (pode escolher um resultado 🔎 do autocomplete ou enviar o texto puro).
- **Esperado:**
  - O Discord mostra "o bot está pensando..." e logo depois a resposta pública `▶️ @você colocou para tocar: [título](...)`.
  - O bot entra no seu canal de voz e o áudio começa em poucos segundos.
  - Aparece o **painel do player** (T07) logo abaixo.
- [ ] passou

### T02. `/play` por URL

- **Objetivo:** tocar por link direto.
- **Pré-condição:** `/stop` antes (nada tocando); você no canal de voz.
- **Passos:**
  1. `/play` (`/tocar`) com `query` = `https://www.youtube.com/watch?v=RGqH4mfmtkQ`.
  2. Aguarde começar. Depois `/stop` e repita com `https://www.youtube.com/watch?v=LCDaw0QmQQc`.
- **Esperado:**
  - Cada URL toca a música correspondente (Kasino – Can't Get Over, depois Kasino no Sabadaço 07/10/2006), com o título correto na resposta e no painel.
  - O autocomplete **não** sugere resultados do YouTube quando o texto começa com `http` (só os atalhos ⭐, se houver).
- [ ] passou

### T03. Autocomplete do `/play`: sugestões 🔎

- **Objetivo:** ver as sugestões do YouTube enquanto digita.
- **Pré-condição:** você ainda não tem músicas com apelido (as ⭐ serão testadas no T34).
- **Passos:**
  1. Digite `/play` (`/tocar`) e, em `query` (`musica`), comece a digitar `kasino sab` **sem enviar**.
  2. Pare de digitar por meio segundo.
  3. Digite só 2 letras (`ka`) e observe.
- **Esperado:**
  - Depois de uma breve pausa (~0,4 s) aparece uma lista com até 25 itens no formato `🔎 título — canal (duração)`. Enquanto você ainda digita rápido, a lista não faz uma busca a cada tecla.
  - Com menos de 3 caracteres, nenhuma sugestão de busca aparece.
  - Escolher um item e enviar toca aquela música.
- [ ] passou

### T04. `/kassino`

- **Objetivo:** o comando de brincadeira toca o vídeo fixo.
- **Pré-condição:** `/stop`; você no canal de voz.
- **Passos:**
  1. Digite `/kassino` e envie.
- **Esperado:**
  - Toca "Kasino no Sabadaço" (a URL 2, `LCDaw0QmQQc`), com a resposta `▶️ @você colocou para tocar: ...` e o painel.
- [ ] passou

### T05. Tocar fora de um canal de voz

- **Objetivo:** o erro deve ser **efêmero** e claro.
- **Pré-condição:** **saia** de todos os canais de voz.
- **Passos:**
  1. `/play` (`/tocar`) com `query` = `kasino sabadaço gilberto barros`.
  2. Repita com `/kassino`.
- **Esperado:**
  - Resposta efêmera (só você vê): `Entre em um canal de voz primeiro.`
  - **Não** fica um "pensando..." público no canal, o bot não entra em canal nenhum e nada é enfileirado.
- [ ] passou

### T06. Segunda música com algo já tocando

- **Objetivo:** a fila funciona.
- **Pré-condição:** uma música tocando (T01); você no canal de voz.
- **Passos:**
  1. `/play` (`/tocar`) com `query` = `https://www.youtube.com/watch?v=LCDaw0QmQQc`.
- **Esperado:**
  - Resposta `➕ @você adicionou [título](...) (posição #1)`.
  - A música atual continua. O contador "📜 N na fila" do painel sobe.
  - Ao fim da atual, a seguinte toca sozinha.
- [ ] passou

---

## B. Painel do player

> Pré-condição geral do grupo: uma música tocando e o painel visível (faça um `/play` do T01). Para ver a fila andando, adicione mais 2 ou 3 músicas com `/play`.

### T07. O painel aparece com tudo

- **Objetivo:** o painel mostra o estado certo.
- **Passos:**
  1. Toque algo com `/play` (`/tocar`) e olhe a mensagem do painel.
- **Esperado:**
  - Borda verde, capa (miniatura), título em link, autor, barra `▬▬🔘▬▬` com `tempo / duração` e `Pedida por @você`.
  - Linha de rodapé no formato `🔊 100% · 🔁 loop desligado · 📜 N na fila · 📦 X KB transmitidos`.
  - Primeira linha de botões: Pausar ⏸️, Pular ⏭️, Parar ⏹️, Embaralhar 🔀, Loop 🔁. Segunda: 🔉 -10, 🔊 +10, 📜 Fila.
  - Só **um** painel por servidor: um segundo `/play` não cria outro painel.
- [ ] passou

### T08. Botão ⏸️ Pausar / ▶️ Continuar

- **Objetivo:** pausar e retomar pelo painel.
- **Passos:**
  1. Clique em **Pausar**.
  2. Clique em **Continuar**.
- **Esperado:**
  - Ao pausar: o áudio para, o título ganha ⏸️, a borda fica **amarela** e o botão vira verde "Continuar" ▶️.
  - Ao continuar: o áudio volta de onde parou, a borda volta a verde e o botão volta a "Pausar".
- [ ] passou

### T09. Botão ⏭️ Pular

- **Objetivo:** pular para a próxima da fila.
- **Pré-condição:** pelo menos 1 música na fila (T06).
- **Passos:**
  1. Clique em **Pular**.
- **Esperado:**
  - A música seguinte começa; o painel mostra o novo título e o contador da fila diminui em 1.
- [ ] passou

### T10. Botão 🔀 Embaralhar

- **Objetivo:** embaralhar a fila.
- **Pré-condição:** 4 ou mais músicas na fila (use `/playlist play` do T31 ou `/play` várias vezes).
- **Passos:**
  1. Abra a fila (botão 📜 ou `/queue`) e anote a ordem.
  2. Clique em **Embaralhar**.
  3. Abra a fila de novo.
- **Esperado:**
  - A ordem mudou (com poucas músicas pode, por sorte, ficar igual; repita). A música atual não muda.
  - Avulsas e faixas de playlist são embaralhadas **cada grupo dentro do seu grupo** (avulsas continuam antes das de playlist).
- [ ] passou

### T11. Botão 🔁 Loop

- **Objetivo:** os três modos de repetição.
- **Passos:**
  1. Clique em **Loop** uma vez e leia o rodapé.
  2. Clique de novo.
  3. Clique de novo.
- **Esperado:**
  - Ciclo no rodapé: `loop desligado` → `repetindo a música` → `repetindo a fila` → `loop desligado`.
  - Com `repetindo a música`: ao acabar, a mesma música toca de novo; **Pular** ainda vai para a próxima.
  - Com `repetindo a fila`: ao acabar, a música vai para o fim da fila e a seguinte toca.
- [ ] passou

### T12. Botões 🔉 / 🔊 volume

- **Objetivo:** ajustar o volume em passos de 10.
- **Passos:**
  1. Clique em **+10** duas vezes.
  2. Clique em **-10** três vezes.
- **Esperado:**
  - O rodapé mostra `🔊 100%`, `110%`, `120%`, depois `110%`, `100%`, `90%`, e o som acompanha.
  - O volume nunca passa de 150% nem fica abaixo de 0% (continue clicando até o limite).
- [ ] passou

### T13. Botão 📜 Fila

- **Objetivo:** abrir a fila a partir do painel.
- **Passos:**
  1. Clique em **Fila**.
- **Esperado:**
  - Abre a fila como mensagem **efêmera** (só você vê), igual ao `/queue` (grupo C).
- [ ] passou

### T14. Botão ⏹️ Parar

- **Objetivo:** parar tudo.
- **Pré-condição:** música tocando e itens na fila.
- **Passos:**
  1. Clique em **Parar**.
- **Esperado:**
  - O áudio para, a fila é esvaziada, o loop volta a desligado e o **bot sai do canal de voz**.
  - O painel vira `💤 Nada tocando — Use /play ou /playlist play para começar.` com só o botão 📜 Fila.
- [ ] passou

### T15. Painel se atualiza sozinho

- **Objetivo:** mudanças por comando de texto também aparecem no painel.
- **Pré-condição:** música tocando e painel visível.
- **Passos:**
  1. Digite `/pause` (`/pausar`).
  2. Digite `/pause` (`/pausar`) de novo.
  3. Digite `/volume` (`/volume`) com `level` (`nivel`) = `60`.
  4. Digite `/skip` (`/pular`).
- **Esperado:**
  - Cada comando muda o painel **sem você clicar em nada**, em cerca de 1 segundo (pausado ⏸️/amarelo e depois verde; `🔊 60%`; música nova).
  - As respostas dos comandos: `⏸️ Pausado.`, `▶️ Continuando.`, `🔊 Volume em 60%.`, `⏭️ @você pulou a música.`
- [ ] passou

### T16. Barra de progresso a cada ~15 s

- **Objetivo:** a barra anda sem ninguém mexer.
- **Pré-condição:** uma música longa tocando (a URL 1 ou 2), **sem pausar**.
- **Passos:**
  1. Anote `tempo / duração` e a posição do 🔘.
  2. Espere cerca de 15 a 30 segundos sem clicar em nada.
- **Esperado:**
  - O tempo e a barra avançam sozinhos a cada ~15 s (não é em tempo real, é de propósito para não estourar o limite de edições do Discord).
  - Pausado, a barra **não** é atualizada.
- [ ] passou

### T17. "KB transmitidos" cresce

- **Objetivo:** o contador de dados enviados funciona.
- **Passos:**
  1. Em uma música recém iniciada, olhe `📦 ... transmitidos` no rodapé.
  2. Espere uma atualização da barra (~15 s) e olhe de novo.
  3. Pule para outra música.
- **Esperado:**
  - O valor cresce (alguns KB por segundo) a cada atualização, em B/KB/MB.
  - Ao trocar de música o contador recomeça do zero para a nova.
- [ ] passou

### T18. `/player` traz o painel de volta

- **Objetivo:** reposicionar o painel no canal atual.
- **Pré-condição:** música tocando; o painel já está um pouco acima no chat (mande umas mensagens ou toque várias músicas).
- **Passos:**
  1. Vá para **outro canal de texto** (ou o mesmo, depois de várias mensagens).
  2. Digite `/player` (`/painel`).
- **Esperado:**
  - Resposta efêmera `🎛️ Painel enviado.`
  - Um painel novo aparece no canal onde você digitou e o **antigo é apagado**.
  - Os botões do novo painel funcionam e o painel continua se atualizando sozinho.
- [ ] passou

---

## C. Fila

> Para testar com bastante coisa, deixe uma música tocando e adicione várias avulsas.

### T19. Fila vazia

- **Objetivo:** mensagem correta sem nada na fila.
- **Pré-condição:** `/stop`; depois que o bot sair do canal.
- **Passos:**
  1. Digite `/queue` (`/fila`).
- **Esperado:**
  - Resposta efêmera `📜 Fila vazia — Use /play para adicionar músicas.`
- [ ] passou

### T20. `/queue` com paginação

- **Objetivo:** a fila lista 10 por página e navega.
- **Pré-condição:** música tocando + **12 ou mais** músicas na fila. Atalho: repita `/play` (`/tocar`) com a URL 2 por 12 vezes (cada uma entra na fila como avulsa).
- **Passos:**
  1. Digite `/queue` (`/fila`).
  2. Clique em ▶️ (próxima página), depois em ◀️ (anterior).
  3. Clique em 🔄.
- **Esperado:**
  - Título `📜 Fila — N música(s)`, linha `Tocando agora: ...`, seção `🎯 Avulsas (tocam primeiro)` e, se houver playlist, `📜 Playlist <nome>`.
  - Rodapé `Página 1 de 2`. ◀️ fica desativado na primeira página e ▶️ na última.
  - Cada linha: `N. [título] · duração · @quem pediu`.
  - 🔄 recarrega a mesma página com os dados atuais.
- [ ] passou

### T21. Remover da fila pelo select

- **Objetivo:** tirar uma música específica.
- **Pré-condição:** pelo menos 3 músicas na fila.
- **Passos:**
  1. `/queue` (`/fila`).
  2. Abra o select `🗑️ Remover da fila…` e escolha a música nº 2.
- **Esperado:**
  - A mensagem é atualizada na hora, sem aquela música; a numeração se ajusta e o total diminui em 1.
  - O painel do player também mostra a nova contagem em ~1 s.
- [ ] passou

### T22. Limpar a fila

- **Objetivo:** esvaziar a fila sem parar a música atual.
- **Pré-condição:** música tocando e fila com itens.
- **Passos:**
  1. `/queue` (`/fila`) e clique em **Limpar fila**.
- **Esperado:**
  - A fila mostra `Nada depois desta música.`, o botão "Limpar fila" fica desativado.
  - A música atual **continua tocando**; ao terminar, o painel mostra "Nada tocando".
- [ ] passou

---

## D. Playlists

> Faça este grupo em ordem: os testes seguintes usam as playlists criadas nos primeiros. Anote os nomes que você usar. Neste roteiro: **`Sabadaço Teste`** (servidor) e **`Global Teste`** (global).

### T23. Sem playlists ainda

- **Objetivo:** mensagens amigáveis quando não há nenhuma.
- **Pré-condição:** conta **sem playlists** (primeira execução depois de subir o container, ver [L](#l-docker)).
- **Passos:**
  1. `/playlist list` (`/playlist listar`).
  2. `/search` (`/buscar`) com `query` (`busca`) = `kasino sabadaço gilberto barros`; no select `💾 Salvar em uma playlist…` escolha o 1º resultado.
- **Esperado:**
  - `/playlist list`: `Você ainda não tem playlists aqui. Crie com /playlist create.`
  - 💾 da busca: erro efêmero `Você ainda não tem playlists. Crie uma com /playlist create.` e o modal **não** abre.
- [ ] passou

### T24. Criar playlist do servidor

- **Objetivo:** criar com o escopo padrão.
- **Passos:**
  1. `/playlist create` (`/playlist criar`) com `name` (`nome`) = `Sabadaço Teste` (deixe `scope` (`escopo`) em branco).
- **Esperado:**
  - Efêmera: `📜 Playlist **Sabadaço Teste** criada (só neste servidor). Adicione músicas com /playlist add ou pelo 💾 da /search.`
  - O nome é limitado a 50 caracteres (o Discord impede mais).
- [ ] passou

### T25. Criar playlist global

- **Objetivo:** criar com escopo global.
- **Passos:**
  1. `/playlist create` (`/playlist criar`) com `name` (`nome`) = `Global Teste` e `scope` (`escopo`) = `Global (todos os servidores)`.
- **Esperado:**
  - Efêmera: `📜 Playlist **Global Teste** criada (global). ...`
  - No cliente em inglês a escolha aparece como `Global (all servers)`; em pt-BR como `Global (todos os servidores)`.
- [ ] passou

### T26. Nome duplicado

- **Objetivo:** não aceitar nomes repetidos no mesmo escopo.
- **Passos:**
  1. Repita `/playlist create` (`/playlist criar`) com `name` (`nome`) = `sabadaço teste` (letras minúsculas), escopo padrão.
- **Esperado:**
  - Erro efêmero `Você já tem uma playlist chamada sabadaço teste.` (a comparação ignora maiúsculas) e nada é criado.
- [ ] passou

### T27. Listar

- **Objetivo:** ver as playlists.
- **Passos:**
  1. `/playlist list` (`/playlist listar`).
- **Esperado:**
  - `📚 Suas playlists` com `- **Sabadaço Teste** · 0 música(s)` e `- **Global Teste** 🌐 · 0 música(s)`. A global tem o 🌐.
- [ ] passou

### T28. Adicionar por busca, com apelido

- **Objetivo:** salvar uma música digitando só o nome.
- **Passos:**
  1. `/playlist add` (`/playlist adicionar`) com `playlist` = `Sabadaço Teste` (escolha no autocomplete), `query` (`musica`) = `kasino sabadaço gilberto barros` e `alias` (`apelido`) = `abertura kasino`.
- **Esperado:**
  - Efêmera (depois de um "pensando..."): `➕ **abertura kasino — <título>** adicionada à playlist **Sabadaço Teste**.`
  - Um nome de playlist inexistente dá `Playlist <nome> não encontrada.`
  - Apelido com mais de 40 caracteres é recusado.
- [ ] passou

### T29. Adicionar por URL

- **Objetivo:** salvar por link, sem apelido.
- **Passos:**
  1. `/playlist add` (`/playlist adicionar`) com `playlist` = `Sabadaço Teste`, `query` (`musica`) = `https://www.youtube.com/watch?v=RGqH4mfmtkQ`.
  2. De novo, agora com `https://www.youtube.com/watch?v=LCDaw0QmQQc`.
  3. Mais uma vez, adicionando a URL 2 também em `Global Teste` com apelido `kassino global`.
- **Esperado:**
  - `➕ **<título>** adicionada à playlist **...**.` (sem prefixo de apelido nos dois primeiros).
  - `Sabadaço Teste` agora tem 3 músicas; `Global Teste` tem 1.
- [ ] passou

### T30. Ver playlist (com botões)

- **Objetivo:** mostrar o conteúdo e os botões.
- **Passos:**
  1. `/playlist show` (`/playlist ver`) com `playlist` = `Sabadaço Teste`.
- **Esperado:**
  - Cabeçalho `📜 Sabadaço Teste`, linha `🏠 deste servidor · 3 música(s) · <duração total>`.
  - Linhas `1. **abertura kasino** — <título> · duração`, numeradas a partir de **1**; apelido em negrito.
  - Botões **▶️ Tocar** e **🔀 Aleatório**. Para uma playlist vazia ambos ficam desativados e aparece `Vazia. Use /playlist add ou o 💾 da busca.`
  - `Global Teste` mostra `🌐 global`.
- [ ] passou

### T31. Tocar playlist

- **Objetivo:** tocar pelo botão e pelo comando.
- **Pré-condição:** `/stop`; você no canal de voz.
- **Passos:**
  1. `/playlist show` (`/playlist ver`) `Sabadaço Teste` e clique em **▶️ Tocar**.
  2. `/stop`. Depois use `/playlist play` (`/playlist tocar`) com `playlist` = `Sabadaço Teste`.
- **Esperado:**
  - `📜 @você adicionou a playlist **Sabadaço Teste** (3 música(s))` e `▶️ Tocando agora: ...`.
  - A primeira música da playlist toca; as outras 2 ficam na fila (no painel: `· 📜 playlist **Sabadaço Teste**`).
  - Pelo botão, a resposta aparece como mensagem pública do canal; sem canal de voz dá o erro do T05.
- [ ] passou

### T32. Tocar playlist em ordem aleatória

- **Objetivo:** embaralhar ao tocar.
- **Pré-condição:** `/stop`.
- **Passos:**
  1. `/playlist play` (`/playlist tocar`) com `playlist` = `Sabadaço Teste` e `shuffle` (`aleatorio`) = `True`. Repita algumas vezes (com `/stop` no meio), ou use o botão **🔀 Aleatório** do `/playlist show`.
- **Esperado:**
  - A ordem de início varia entre as execuções (com 3 músicas pode repetir por sorte; tente 3 vezes). Nenhuma música some.
- [ ] passou

### T33. Salvar a música atual (`add-current`)

- **Objetivo:** guardar na playlist o que está tocando.
- **Passos:**
  1. Com algo tocando (`/play` com a URL 1), use `/playlist add-current` (`/playlist salvar-atual`) com `playlist` = `Global Teste` e `alias` (`apelido`) = `can't get over`.
  2. Dê `/stop` e repita o comando.
- **Esperado:**
  - Com música: efêmera `💾 **can't get over — <título>** salva na playlist **Global Teste**.`
  - Sem nada tocando: erro efêmero `Nada tocando agora.`
- [ ] passou

### T34. Apelido: definir e remover (e o ⭐ no `/play`)

- **Objetivo:** apelidos aparecem como atalho no autocomplete.
- **Passos:**
  1. `/playlist alias` (`/playlist apelidar`) com `playlist` = `Sabadaço Teste`, `track` (`musica`) = `2` (escolha no autocomplete, que lista `1. ...`, `2. ...`) e `alias` (`apelido`) = `sabadaço top`.
  2. Digite `/play` (`/tocar`) e, em `query` (`musica`), escreva `sabadaço`.
  3. Repita o `/playlist alias` do passo 1, **deixando `alias` vazio**.
- **Esperado:**
  - Passo 1: `🏷️ **<título>** agora se chama **sabadaço top**.`
  - Passo 2: o autocomplete mostra primeiro as sugestões `⭐ sabadaço top — <título>` (suas músicas com apelido, de qualquer playlist visível) e depois as `🔎` do YouTube. Escolher a ⭐ toca aquela música.
  - Passo 3: `🏷️ Apelido removido de **<título>**.` e a ⭐ deixa de aparecer.
- [ ] passou

### T35. Remover música

- **Objetivo:** tirar uma faixa da playlist.
- **Passos:**
  1. `/playlist remove` (`/playlist remover`) com `playlist` = `Sabadaço Teste` e `track` (`musica`) = `3`.
  2. Tente de novo com `track` = `9` (digite o número na mão).
- **Esperado:**
  - Passo 1: `🗑️ **<título>** removida de **Sabadaço Teste**.` (a playlist fica com 2 músicas).
  - Passo 2: erro `Música #9 não existe na playlist Sabadaço Teste.`
- [ ] passou

### T36. Mover música entre playlists (levando o apelido)

- **Objetivo:** mover sem perder o apelido.
- **Pré-condição:** `Sabadaço Teste` com uma música com apelido (refaça o alias do T34, passo 1, se tirou).
- **Passos:**
  1. `/playlist move` (`/playlist mover`) com `from` (`de`) = `Sabadaço Teste`, `track` (`musica`) = a música com apelido, `to` (`para`) = `Global Teste`.
  2. `/playlist show` (`/playlist ver`) das duas.
  3. Tente mover com `from` e `to` iguais.
- **Esperado:**
  - `↪️ **<apelido — título>** movida de **Sabadaço Teste** para **Global Teste**.`
  - Na origem a música sumiu; no destino ela está no fim, **com o apelido**.
  - Origem = destino: erro `Escolha playlists diferentes.`
- [ ] passou

### T37. Renomear

- **Objetivo:** trocar o nome.
- **Passos:**
  1. `/playlist rename` (`/playlist renomear`) com `playlist` = `Sabadaço Teste` e `name` (`nome`) = `Kasino Hits`.
  2. Tente renomear `Kasino Hits` para `Global Teste` (já existe como global; como o escopo é outro, o nome é permitido) e depois crie outra de servidor chamada `Kasino Hits` e tente renomear para o mesmo nome.
- **Esperado:**
  - `✏️ **Sabadaço Teste** agora se chama **Kasino Hits**.` (a partir daqui use esse nome).
  - Duplicado no mesmo escopo: `Você já tem uma playlist chamada ...`
- [ ] passou

### T38. Apagar com confirmação

- **Objetivo:** apagar só depois de confirmar.
- **Passos:**
  1. Crie `Lixo` (`/playlist create`) com 1 música.
  2. `/playlist delete` (`/playlist apagar`) com `playlist` = `Lixo`. Clique em **Cancelar**.
  3. Rode `/playlist delete` de novo e clique em **Apagar**.
  4. `/playlist list` (`/playlist listar`).
- **Esperado:**
  - A mensagem pergunta `Apagar a playlist **Lixo** com 1 música(s)? Não dá para desfazer.` com os botões **Apagar** (vermelho) e **Cancelar**.
  - Cancelar: `Ok, nada foi apagado.` e a playlist continua na lista.
  - Apagar: `🗑️ Playlist **Lixo** apagada.` e ela some da lista.
- [ ] passou

### T39. Visibilidade entre servidores

- **Objetivo:** playlist de servidor fica no servidor; global vale em qualquer um.
- **Pré-condição:** o bot está em **dois servidores** e os comandos aparecem nos dois (sem `DISCORD_DEV_GUILD_ID` ou com os comandos registrados nos dois). Você tem `Kasino Hits` (servidor A) e `Global Teste` (global).
- **Passos:**
  1. No **servidor B**, digite `/playlist list` (`/playlist listar`).
  2. No servidor B, abra o autocomplete de `playlist` em `/playlist show` (`/playlist ver`).
  3. No servidor B, escreva à mão `Kasino Hits` em `/playlist show` e envie.
  4. No servidor B, toque `Global Teste` (`/playlist play` / `/playlist tocar`).
- **Esperado:**
  - Servidor B lista **só** `Global Teste 🌐` (a `Kasino Hits` não aparece, nem no autocomplete).
  - Passo 3: `Playlist Kasino Hits não encontrada.`
  - Passo 4: a global toca normalmente no servidor B.
- [ ] passou

### T40. Outra pessoa não vê nem altera suas playlists

- **Objetivo:** playlists são do dono.
- **Pré-condição:** uma **segunda conta** do Discord no mesmo servidor, sem playlists.
- **Passos:**
  1. Na conta 2, `/playlist list` (`/playlist listar`).
  2. Na conta 2, `/playlist show` (`/playlist ver`) escrevendo na mão `Kasino Hits`.
  3. Na conta 2, tente `/playlist delete` (`/playlist apagar`), `/playlist rename` (`/playlist renomear`) e `/playlist add` (`/playlist adicionar`) com esse mesmo nome escrito à mão.
- **Esperado:**
  - Passo 1: mensagem de que não há playlists. Passo 2 e 3: `Playlist Kasino Hits não encontrada.` em todos.
  - O autocomplete da conta 2 não sugere as suas playlists.
  - Confirme na sua conta que `Kasino Hits` continua intacta (nome e músicas).
- [ ] passou

---

## E. Busca

> Pré-condição geral: você tem a playlist `Kasino Hits` (ou outra) e está num canal de voz.

### T41. `/search` mostra 5 resultados

- **Objetivo:** modo busca com resultados.
- **Passos:**
  1. `/search` (`/buscar`) com `query` (`busca`) = `kasino sabadaço gilberto barros`.
- **Esperado:**
  - Resposta **efêmera** (depois de "pensando..."): `🔎 Resultados para *kasino sabadaço gilberto barros*`.
  - Até 5 resultados: `1. [título](link)` com `canal · duração` e um botão ▶️ em cada.
  - No fim, o select `💾 Salvar em uma playlist…` com os mesmos resultados.
  - Repetir a mesma busca em seguida é mais rápido (cache de 5 min).
- [ ] passou

### T42. Botão ▶️ do resultado

- **Objetivo:** tocar direto da busca.
- **Pré-condição:** você no canal de voz.
- **Passos:**
  1. Do T41, clique no ▶️ do 1º resultado.
- **Esperado:**
  - Resposta `▶️ @você colocou para tocar: ...` (ou `➕ ... (posição #N)` se já houver algo tocando), o bot entra no canal e o painel aparece/atualiza.
  - Fora do canal de voz: erro `Entre em um canal de voz primeiro.`
- [ ] passou

### T43. 💾 Salvar em playlist (modal com playlist + apelido)

- **Objetivo:** salvar um resultado escolhendo a playlist e o apelido.
- **Passos:**
  1. No `/search` do T41, abra o select `💾 Salvar em uma playlist…` e escolha o resultado 1.
  2. No modal **Salvar em playlist**, escolha a playlist `Kasino Hits` e escreva o apelido `kasino da busca`. Envie.
  3. Repita escolhendo outro resultado, com o apelido em branco.
  4. Confira com `/playlist show` (`/playlist ver`) `Kasino Hits`.
- **Esperado:**
  - O modal tem o campo **Playlist** (seleção com suas playlists, as globais marcadas `(global)`) e **Apelido (opcional)** (placeholder `ex.: abertura do kassino`, máximo 40).
  - Efêmera: `💾 **<título>** salva na playlist **Kasino Hits**.`
  - Na playlist, a 1ª entrada aparece com o apelido `kasino da busca` e a 2ª sem apelido.
- [ ] passou

---

## F. Regra de prioridade (avulsa × playlist)

> A fila tem duas pistas: **avulsas** (`/play`, busca) tocam sempre **antes** das faixas de **playlist**. O usuário é avisado nos dois sentidos. Use uma playlist com 3 ou mais músicas (complete `Kasino Hits` com `/playlist add` se preciso).

### T44. Playlist tocando e depois um `/play` avulso

- **Objetivo:** aviso quando a avulsa passa na frente das faixas restantes da playlist.
- **Pré-condição:** `/stop`; você no canal de voz.
- **Passos:**
  1. `/playlist play` (`/playlist tocar`) `Kasino Hits` (3 ou mais músicas; 1 toca, o resto fica na fila).
  2. `/play` (`/tocar`) com `query` = `https://www.youtube.com/watch?v=RGqH4mfmtkQ`.
- **Esperado:**
  - A resposta do passo 2 traz `➕ @você adicionou ... (posição #N)` e a linha `⚠️ Músicas avulsas têm prioridade: esta toca antes das N música(s) restantes da playlist **Kasino Hits**.`
  - Em `/queue` (`/fila`) a avulsa aparece **primeiro**, em `🎯 Avulsas (tocam primeiro)`, e as da playlist abaixo.
  - Quando a música atual acabar, toca a avulsa e só depois as da playlist.
- [ ] passou

### T45. Avulsas na fila e depois uma playlist

- **Objetivo:** aviso no sentido contrário.
- **Pré-condição:** `/stop`; você no canal de voz.
- **Passos:**
  1. `/play` (`/tocar`) com a URL 1 (começa a tocar).
  2. `/play` (`/tocar`) com a URL 2 e depois com `kasino sabadaço gilberto barros` (2 avulsas na fila).
  3. `/playlist play` (`/playlist tocar`) `Kasino Hits`.
- **Esperado:**
  - A resposta do passo 3: `📜 @você adicionou a playlist **Kasino Hits** (N música(s))` e `⚠️ Músicas avulsas têm prioridade: as 2 que já estão na fila tocam antes da playlist.`
  - A playlist **não** começa agora (a música 1 continua).
- [ ] passou

### T46. Ordem real de execução

- **Objetivo:** conferir que a regra vale na prática.
- **Pré-condição:** estado do T45 (1 tocando, 2 avulsas e a playlist na fila).
- **Passos:**
  1. Abra `/queue` (`/fila`) e anote a ordem.
  2. Clique em **Pular** no painel 3 vezes, observando o título a cada vez.
- **Esperado:**
  - A fila mostra primeiro as 2 avulsas (numeradas 1 e 2) e depois a playlist.
  - Ao pular, tocam: avulsa 1, avulsa 2, e só então as faixas da playlist na ordem dela.
- [ ] passou

---

## G. Controles

### T47. `/pause`

- **Objetivo:** pausar e retomar por comando.
- **Pré-condição:** música tocando.
- **Passos:**
  1. `/pause` (`/pausar`) duas vezes.
- **Esperado:**
  - 1ª vez: `⏸️ Pausado.` (áudio para). 2ª vez: `▶️ Continuando.` (áudio volta). O painel acompanha.
- [ ] passou

### T48. `/skip`

- **Objetivo:** pular por comando.
- **Pré-condição:** música tocando e pelo menos 1 na fila.
- **Passos:**
  1. `/skip` (`/pular`).
- **Esperado:**
  - `⏭️ @você pulou a música.` e a próxima começa. Se não houver próxima, o painel passa a "Nada tocando".
- [ ] passou

### T49. `/volume` de 0 a 150

- **Objetivo:** faixa e limites do volume.
- **Pré-condição:** música tocando.
- **Passos:**
  1. `/volume` com `level` (`nivel`) = `0`, depois `100`, depois `150`.
  2. Tente digitar `151` e também `-1` no campo.
- **Esperado:**
  - `🔊 Volume em 0%.` (mudo), `100%` (normal), `150%` (mais alto). O rodapé do painel acompanha.
  - O Discord **não deixa enviar** `151` nem valores negativos (aviso de valor fora do intervalo no próprio campo).
- [ ] passou

### T50. `/stop` sai do canal

- **Objetivo:** parar tudo e sair.
- **Pré-condição:** música tocando e fila com itens.
- **Passos:**
  1. `/stop` (`/parar`).
  2. Olhe o canal de voz e o `/queue` (`/fila`).
- **Esperado:**
  - `⏹️ @você parou o player.`
  - O bot **sai do canal de voz**, a fila fica vazia (`Fila vazia`) e o painel mostra `💤 Nada tocando`.
  - Um novo `/play` volta a funcionar normalmente (o bot entra de novo).
- [ ] passou

---

## H. Localização (pt-BR)

### T51. Cliente Discord em português

- **Objetivo:** os comandos aparecem traduzidos.
- **Pré-condição:** Discord → Configurações → Idioma: **Português (Brasil)**. Se mudou agora, reinicie o app (Ctrl+R).
- **Passos:**
  1. No canal, digite `/` e filtre pelo bot.
  2. Confira cada comando e algumas opções (veja a [tabela de nomes](#tabela-de-nomes-dos-comandos)).
- **Esperado:**
  - Aparecem `/tocar`, `/buscar`, `/painel`, `/fila`, `/pular`, `/parar`, `/pausar`, `/volume`, `/kassino`, `/ajuda` e `/playlist` com os subcomandos `criar`, `listar`, `ver`, `tocar`, `adicionar`, `salvar-atual`, `apelidar`, `remover`, `mover`, `renomear`, `apagar`.
  - Opções traduzidas (`musica`, `busca`, `nivel`, `comando`, `nome`, `escopo`, `aleatorio`, `apelido`, `de`, `para`) e descrições em português.
  - As escolhas do escopo aparecem como `Só neste servidor` e `Global (todos os servidores)`.
  - Todos funcionam igual ao inglês.
- [ ] passou

### T52. Cliente Discord em inglês

- **Objetivo:** o padrão em inglês continua intacto.
- **Passos:**
  1. Mude o idioma do Discord para **English (US)** (e reinicie o app).
  2. Digite `/` e confira os comandos.
- **Esperado:**
  - Aparecem `/play`, `/search`, `/player`, `/queue`, `/skip`, `/stop`, `/pause`, `/volume`, `/kassino`, `/help` e `/playlist create|list|show|play|add|add-current|alias|remove|move|rename|delete`, com descrições em inglês.
  - Os textos de **resposta** do bot continuam em português nos dois casos (só a definição dos comandos é traduzida).
- [ ] passou

---

## I. Erros

### T53. URL inválida ou música que não carrega

- **Objetivo:** o erro é amigável e o bot não quebra.
- **Passos:**
  1. `/play` (`/tocar`) com `query` = `https://www.youtube.com/watch?v=ZZZZZZZZZZZ` (vídeo que não existe).
  2. `/play` (`/tocar`) com `query` = `https://exemplo.invalid/musica.mp3`.
  3. Depois, `/play` com a URL 1 para confirmar que o bot segue funcionando.
- **Esperado:**
  - Mensagem curta do tipo `Erro ao carregar música: ...` ou `Nenhuma música encontrada para: ...` (a mensagem exata depende do YouTube), sem travar em "pensando..." por mais de ~20 s.
  - Nada é enfileirado e o painel/fila continuam como estavam.
  - O passo 3 toca normalmente.
- [ ] passou

### T54. Busca sem resultado

- **Objetivo:** buscas sem resultado respondem bem.
- **Passos:**
  1. `/search` (`/buscar`) com `query` (`busca`) = `qzxwvjkpyyhhgfdsa 91827364`.
  2. `/play` (`/tocar`) com `query` = o mesmo texto.
- **Esperado:**
  - `/search`: `🔎 Nada encontrado para *...*`.
  - `/play`: `Nenhuma música encontrada para: ...` (se o YouTube devolver algum resultado aproximado, o resultado pode ser uma música qualquer; repita com outro texto sem sentido).
- [ ] passou

### T55. Controle sem nada tocando

- **Objetivo:** erro claro quando o servidor não tem player.
- **Pré-condição:** acabou de reiniciar o container (`docker compose restart`) e **ninguém** usou `/play` desde então.
- **Passos:**
  1. `/pause` (`/pausar`).
  2. `/volume` com `level` (`nivel`) = `50`.
- **Esperado:**
  - Erro efêmero `Nada tocando neste servidor.` nos dois casos. O bot continua respondendo normalmente depois.
- [ ] passou

### T56. Tocar playlist vazia

- **Objetivo:** não tocar o que não existe.
- **Passos:**
  1. Crie `Vazia` (`/playlist create`) sem músicas.
  2. `/playlist show` (`/playlist ver`) `Vazia`: os botões Tocar e Aleatório estão desativados.
  3. `/playlist play` (`/playlist tocar`) `Vazia`.
- **Esperado:**
  - `/playlist show` mostra `Vazia. Use /playlist add ou o 💾 da busca.`
  - `/playlist play`: erro `A playlist Vazia está vazia.`
- [ ] passou

---

## J. Painel admin

> Abra http://localhost:8080 (ou a porta de `ADMIN_PORT`). Deixe o Discord aberto ao lado para ver os efeitos. Para os testes com player, mantenha uma música tocando no servidor de teste.

### T57. Login e logout

- **Objetivo:** só entra quem tem a senha.
- **Passos:**
  1. Abra http://localhost:8080/admin sem estar logado.
  2. Entre com o usuário certo e uma senha **errada**.
  3. Entre com `ADMIN_USER` e `ADMIN_PASSWORD`.
  4. Clique em **Sair** no menu.
- **Esperado:**
  - Passo 1: redireciona para a tela de login.
  - Passo 2: não entra (volta ao login com erro).
  - Passo 3: abre a página **Servidores**, com o menu `🎶 Sabadaço · Servidores · Playlists · Métricas · Sair`.
  - Passo 4: volta à tela de login e `/admin` volta a pedir senha.
- [ ] passou

### T58. Servidores ao vivo

- **Objetivo:** o painel inicial reflete o que acontece.
- **Pré-condição:** logado; música tocando.
- **Passos:**
  1. Na página **Servidores**, observe o cartão do seu servidor sem recarregar a página.
  2. Em outra aba/Discord, toque outra música ou pause.
  3. Olhe o topo da página (contadores).
- **Esperado:**
  - O cartão mostra o selo `tocando`/`pausado`/`parado`, capa, título em link, autor, barra de progresso, `tempo / duração · N na fila · vol X% · Y transmitidos`.
  - Os dados se atualizam sozinhos a cada ~2 s, sem recarregar. Servidores tocando vêm primeiro.
  - Contadores do topo: `baixados das fontes`, `enviados ao Discord`, `músicas tocadas`, `comandos`, `buscas` crescem conforme você usa o bot (atualizam ao recarregar a página).
- [ ] passou

### T59. Pausar, pular e parar pelo admin (e o Discord acompanha)

- **Objetivo:** o admin comanda o player e o painel do Discord muda sozinho.
- **Pré-condição:** música tocando com mais algumas na fila; o painel do Discord visível ao lado.
- **Passos:**
  1. No cartão do servidor, clique em **⏸️ Pausar**; depois em **▶️ Continuar**.
  2. Clique em **⏭️ Pular**.
  3. Clique em **⏹️ Parar**.
- **Esperado:**
  - Cada clique funciona e o cartão atualiza na hora.
  - O painel do Discord (mensagem no chat) é editado em ~1 s sem ninguém tocar nele: borda amarela ao pausar, nova música ao pular, `💤 Nada tocando` ao parar.
  - Ao parar, o bot sai do canal de voz.
- [ ] passou

### T60. Página do servidor: controles

- **Objetivo:** controles completos de um servidor.
- **Pré-condição:** música tocando.
- **Passos:**
  1. Clique no nome do servidor no cartão (abre `/admin/guilds/<id>`).
  2. Teste **🔀 Embaralhar**, **🔁 Loop** (três vezes), **🔉 -10**, **🔊 +10**.
  3. Confira o texto `Pedida por ...`.
- **Esperado:**
  - Cada botão aplica a ação e a tela atualiza (loop alterna `loop desligado`/`repetindo a música`/`repetindo a fila`; volume muda de 10 em 10 e fica entre 0 e 150).
  - O painel do Discord reflete as mudanças.
  - Em músicas pedidas pelo painel aparece `Pedida por painel admin`.
- [ ] passou

### T61. Fila: subir, descer, remover, limpar

- **Objetivo:** gerir a fila pela tela.
- **Pré-condição:** pelo menos 4 avulsas na fila (use `/play` várias vezes) na página do servidor.
- **Passos:**
  1. Na tabela **Fila (N)**, clique em **↑** numa linha do meio; depois em **↓**.
  2. Clique em **🗑️** numa linha.
  3. Clique em **Limpar fila** e confirme no aviso `Limpar a fila inteira?`.
- **Esperado:**
  - ↑/↓ trocam a posição da música (o primeiro item não tem ↑ e o último não tem ↓).
  - 🗑️ remove só aquela linha; o total no título diminui.
  - Limpar fila esvazia a fila, mas a música atual continua tocando. Cancelar o aviso não limpa nada.
  - O painel do Discord (`📜 N na fila`) acompanha.
- [ ] passou

### T62. Erro ao mover entre grupos (toast)

- **Objetivo:** a regra de prioridade vale no admin e o erro aparece como toast.
- **Pré-condição:** fila com **avulsas e músicas de playlist** (como no T45). Na tabela, a coluna **Origem** mostra `🎯 avulsa` ou `📜 <playlist>`.
- **Passos:**
  1. Na última avulsa, clique em **↓** (tentar passar para dentro do grupo da playlist).
  2. Ou na primeira da playlist, clique em **↑**.
- **Esperado:**
  - Um **toast** de erro no topo: `❌ Só é possível mover dentro do mesmo grupo (avulsas ou playlist).`, que some depois de ~4 s. A fila não muda e a página não recarrega.
- [ ] passou

### T63. Tocar música pelo admin

- **Objetivo:** pedir uma música pela tela.
- **Passos:**
  1. Na página do servidor, no cartão **Tocar música**, escolha o **Canal de voz**, escreva `kasino sabadaço gilberto barros` (ou uma URL) em **URL ou busca** e clique em **▶️ Adicionar (avulsa)**.
- **Esperado:**
  - A música entra como **avulsa**; se não havia nada tocando, o bot entra no canal escolhido e começa a tocar.
  - Na fila/atual aparece `Pedida por painel admin`.
  - Se já havia o painel no Discord, ele atualiza; se não havia, a música toca mesmo assim (o painel do Discord só é criado por comandos do Discord).
  - Um texto sem resultado dá toast `❌ Nenhuma música encontrada para: ...`.
- [ ] passou

### T64. Tocar playlist pelo admin

- **Objetivo:** tocar uma playlist de qualquer pessoa.
- **Passos:**
  1. No cartão **Tocar playlist**, escolha o canal de voz, uma playlist (o nome mostra o dono e a quantidade, ex.: `Kasino Hits — <dono> (3)`) e, opcionalmente, marque **Ordem aleatória**.
  2. Clique em **📜 Tocar playlist**.
- **Esperado:**
  - As faixas entram na pista de playlist (aparecem como `📜 <nome>` na fila) e o aviso de prioridade se aplica (avulsas passam na frente).
  - Com **Ordem aleatória** a ordem varia.
  - Listadas: as playlists daquele servidor e as globais.
- [ ] passou

### T65. Playlists: lista e filtros

- **Objetivo:** ver e filtrar as playlists por pessoa e servidor.
- **Passos:**
  1. Clique em **Playlists** no menu.
  2. No filtro **Dono (id)**, escolha/cole o seu ID de usuário (a lista de sugestões traz os usuários que já usaram o bot) e clique em **Filtrar**.
  3. Filtre por **Servidor**. Clique em **Limpar**.
- **Esperado:**
  - A tabela mostra `Nome · Dono · Escopo · Servidor · Músicas` (globais com `🌐 global`, as de servidor com `🏠 servidor`; a global mostra `—` no servidor).
  - Os filtros restringem a lista; "Limpar" mostra tudo. Sem resultado: `Nenhuma playlist.`
- [ ] passou

### T66. Criar playlist pelo admin

- **Objetivo:** criar em nome de uma pessoa.
- **Passos:**
  1. Em **Nova playlist**, preencha **Dono (id do usuário)** com o seu ID, **Nome** = `Criada no admin`, **Escopo** = `Só no servidor` e **Servidor** = o seu servidor de teste. Clique em **Criar**.
  2. Repita com **Escopo** = `Só no servidor` e **Servidor** = `—`.
  3. Repita com **Escopo** = `Global`.
- **Esperado:**
  - Passo 1: abre a página da playlist nova. No Discord, `/playlist list` (`/playlist listar`) mostra a playlist para essa pessoa.
  - Passo 2: mensagem de erro `Escolha o servidor para uma playlist de servidor.` no topo da página.
  - Passo 3: cria a global (a escolha do servidor é ignorada).
- [ ] passou

### T67. Renomear, adicionar música e apelidar

- **Objetivo:** editar uma playlist.
- **Passos:**
  1. Abra a playlist `Criada no admin`. Em **Renomear**, troque para `Admin OK` e clique em **Salvar**.
  2. Em **Adicionar música**, informe `https://www.youtube.com/watch?v=RGqH4mfmtkQ` e apelido `admin kasino`; clique em **Adicionar**.
  3. Adicione também `kasino sabadaço gilberto barros` sem apelido.
  4. Na linha da 2ª música, escreva um apelido e clique em **🏷️**; depois apague o texto e clique em 🏷️ de novo.
- **Esperado:**
  - Título e lista atualizados após cada passo (a página recarrega). A tabela mostra `# · Música (título, autor, duração) · Apelido · Mover para`.
  - No Discord, `/playlist show` (`/playlist ver`) mostra o novo nome, as músicas e os apelidos; o apelido aparece no ⭐ do `/play` para o dono.
  - Nome em branco ou repetido dá a mensagem de erro no topo da página.
- [ ] passou

### T68. Mover e remover música no admin

- **Objetivo:** gerir músicas entre playlists da mesma pessoa.
- **Pré-condição:** o mesmo dono tem 2 ou mais playlists.
- **Passos:**
  1. Numa música com apelido, escolha outra playlist no select da coluna **Mover para** e clique em **↪️**.
  2. Abra a playlist de destino.
  3. Clique em **🗑️** numa música.
- **Esperado:**
  - A música sai da origem e aparece no fim do destino **com o apelido**. O select lista só playlists **da mesma pessoa**.
  - 🗑️ remove a música da lista.
- [ ] passou

### T69. Apagar playlist no admin

- **Objetivo:** apagar com confirmação.
- **Passos:**
  1. Na página de uma playlist, clique em **Apagar playlist**; no aviso do navegador clique em **Cancelar**.
  2. Clique de novo e confirme com **OK**.
- **Esperado:**
  - Cancelar: nada acontece. OK: volta à lista de playlists e ela não aparece mais (nem no `/playlist list` do dono no Discord).
- [ ] passou

### T70. Erros no admin (toast e mensagem)

- **Objetivo:** erros de regra aparecem na tela, sem página branca.
- **Passos:**
  1. Em **Tocar música**, deixe a fila com algo e use `qzxwvjkpyyhhgfdsa 91827364` como busca.
  2. Em **Playlists → Nova playlist**, crie duas vezes a mesma playlist (mesmo dono, escopo e nome).
  3. Em outro momento, pare o player e clique no botão **⏭️ Pular** de um cartão antigo (abra duas abas, pare em uma, clique na outra).
- **Esperado:**
  - Ações via botão/formulário dinâmico (passos 1 e 3): toast `❌ <mensagem>` que desaparece sozinho em alguns segundos.
  - Ações de formulário de playlist (passo 2): faixa de erro no topo com `Você já tem uma playlist chamada ...`.
  - Nenhum erro 500 / "Whitelabel Error Page".
- [ ] passou

---

## K. Métricas

### T71. `/actuator/prometheus` pede senha

- **Objetivo:** só quem tem as credenciais lê as métricas.
- **Passos:**
  1. Abra http://localhost:8080/actuator/prometheus sem login (janela anônima) ou rode `curl -i http://localhost:8080/actuator/prometheus`.
  2. Rode `curl -u admin:SUA_SENHA http://localhost:8080/actuator/prometheus` (troque usuário e senha pelos de `ADMIN_USER`/`ADMIN_PASSWORD`).
  3. Abra http://localhost:8080/actuator/health sem login.
- **Esperado:**
  - Passo 1: sem autenticação, `401`/redirecionamento ao login.
  - Passo 2: texto de métricas do Prometheus.
  - Passo 3: `{"status":"UP"}` sem pedir senha.
- [ ] passou

### T72. Métricas `sabadaco_*`

- **Objetivo:** as métricas do bot são geradas e crescem com o uso.
- **Pré-condição:** já tocou algumas músicas, pulou uma, fez buscas e comandos.
- **Passos:**
  1. `curl -s -u admin:SUA_SENHA http://localhost:8080/actuator/prometheus | grep sabadaco_`
  2. Toque e pule mais uma música, repita o comando e compare.
- **Esperado:**
  - Aparecem séries com o prefixo `sabadaco_` (o Prometheus troca `.` por `_` e acrescenta sufixos como `_total` e `_seconds`):

    | Série (prefixo) | O que deve mostrar |
    |---|---|
    | `sabadaco_audio_downloaded_bytes` | bytes baixados, com `source` (`youtube`...) |
    | `sabadaco_audio_sent_bytes` | bytes enviados ao Discord, com `guild` |
    | `sabadaco_track_bytes` | bytes por música (contagem, soma, máximo), com `source` |
    | `sabadaco_track_listened` | tempo ouvido, com `reason` (`FINISHED`, `REPLACED`, `STOPPED`...) |
    | `sabadaco_tracks_played` / `sabadaco_tracks_failed` | tocadas / falhas, com `source` e `origin` (`single`/`playlist`) |
    | `sabadaco_players_active` | servidores tocando agora |
    | `sabadaco_queue_size` | tamanho da fila, com `guild` |
    | `sabadaco_playlists_saved` | quantidade de playlists |
    | `sabadaco_commands` / `sabadaco_components` | tempo/contagem por `command`/`component` e `outcome` |
    | `sabadaco_searches` | buscas, com `outcome` (`found`/`empty`/`cached`) |

  - Os valores aumentam depois do passo 2 (por exemplo `sabadaco_tracks_played`, `sabadaco_commands` com `command="play"`, `sabadaco_track_listened` com `reason="REPLACED"` depois de um skip).
  - Repetir a mesma busca faz `outcome="cached"` subir.
- [ ] passou

---

## L. Docker

### T73. Reiniciar o container perde as playlists (esperado)

- **Objetivo:** confirmar o comportamento atual: playlists ficam só na memória.
- **Pré-condição:** você tem pelo menos uma playlist salva.
- **Passos:**
  1. `docker compose restart`
  2. Acompanhe `docker compose logs -f` até aparecer de novo o registro dos comandos.
  3. `/playlist list` (`/playlist listar`).
  4. Abra o admin → **Playlists**.
- **Esperado:**
  - O bot volta sozinho (`restart: unless-stopped`), registra os comandos de novo e responde.
  - `/playlist list` e o admin mostram **nenhuma playlist**: isso é **esperado** (armazenamento em memória; veja "Limitações conhecidas" no [ARQUITETURA.md](ARQUITETURA.md#12-limitações-conhecidas)).
  - Qualquer painel de player antigo no chat deixa de ser atualizado (o bot esqueceu a mensagem); `/play` cria um painel novo.
- [ ] passou

---

## M. Ajuda (`/help`)

> O `/help` é montado automaticamente a partir do que cada comando declara em `help()`. A resposta é sempre **efêmera** (só você vê). Em pt-BR o comando é `/ajuda` e a opção é `comando`.

### T74. `/help`: lista por categoria

- **Objetivo:** ver todos os comandos organizados.
- **Passos:**
  1. Digite `/help` (`/ajuda`) sem opção e envie.
- **Esperado:**
  - Mensagem efêmera `📖 Comandos do Sabadaço`, com os comandos agrupados nas categorias **🎵 Música** (`/play`, `/search`), **🎛️ Fila e controles** (`/pause`, `/player`, `/queue`, `/skip`, `/stop`, `/volume`), **💾 Playlists** (os 11 `/playlist ...`) e **✨ Outros** (`/help`, `/kassino`).
  - Cada comando aparece como `` `/play` `` seguido de um resumo de uma linha (ex.: `/skip`: "Pula para a próxima música.").
  - Rodapé explicando que em português os comandos aparecem traduzidos (ex.: `/play` é `/tocar`) e que `/help comando` vai direto a um deles.
  - Um select `🔍 Ver exemplos de um comando…` com os comandos (cada opção com o resumo).
- [ ] passou

### T75. `/help`: detalhes pelo select, com exemplos

- **Objetivo:** ver o detalhe de um comando.
- **Passos:**
  1. No `/help` (`/ajuda`), abra o select e escolha `/play`.
  2. Repita escolhendo `/playlist add` e `/kassino`.
- **Esperado:**
  - A **mesma mensagem é editada** para o detalhe: título `/play`, linha com a categoria e `em português: /tocar`, o resumo, a seção **Exemplos** (cada um num bloco de código seguido da explicação) e, quando houver, a seção **Dicas**.
  - Os exemplos usam músicas do Kasino com Gilberto Barros (ex.: a URL `https://www.youtube.com/watch?v=RGqH4mfmtkQ`) e cada um começa com o próprio comando, que dá para copiar e usar.
  - Para `/playlist add`, o nome em português mostrado é `/playlist adicionar`.
- [ ] passou

### T76. `/help`: botão voltar

- **Objetivo:** voltar da tela de detalhes.
- **Passos:**
  1. Na tela de detalhes (T75), clique em **◀️ Todos os comandos**.
- **Esperado:**
  - A mensagem volta para a lista por categoria (T74), com o select de novo disponível; dá para escolher outro comando.
- [ ] passou

### T77. `/help comando:<nome>` direto

- **Objetivo:** ir direto ao detalhe, em inglês ou em português.
- **Passos:**
  1. `/help` (`/ajuda`) e, em `command` (`comando`), comece a digitar `play` **sem enviar**: confira o autocomplete.
  2. Envie com `play`. Repita com `tocar`, `/playlist move`, `playlist mover` e `PLAY` (maiúsculas).
  3. Envie com `abcxyz`.
- **Esperado:**
  - O autocomplete lista até 25 sugestões no formato `/play · /tocar` (casa com o nome em inglês **ou** em português).
  - Passos 1 e 2: o detalhe do comando abre direto, sem passar pela lista, e traz o botão **◀️ Todos os comandos**. Nome em inglês, em português, com ou sem `/` e em maiúsculas, tudo leva ao mesmo comando.
  - Passo 3: erro efêmero `Comando abcxyz não encontrado. Use /help para ver a lista.`
- [ ] passou

---

## N. Em desenvolvimento (a confirmar)

> **Atenção:** esta seção descreve funcionalidades **ainda em implementação**. Os passos e resultados abaixo são a **intenção**, não o comportamento final. Eles serão **revisados quando a implementação terminar**, e cada teste que for entregue sai daqui e ganha os passos reais nos grupos acima. Não conte estes resultados como validação até lá.

### TD01. Admin: temas escuro e creme claro *(a confirmar)*

- **Objetivo:** nova aparência do painel admin.
- **Passos:**
  1. Abra o admin.
- **Esperado (a confirmar):**
  - O painel usa o tema **escuro** ou o **creme claro**, de forma legível em todas as telas (servidores, servidor, playlists).
- [ ] passou

### TD02. Admin: alternar tema *(a confirmar)*

- **Objetivo:** trocar entre escuro e creme claro.
- **Passos:**
  1. Use o controle de alternância de tema do painel.
  2. Recarregue a página.
- **Esperado (a confirmar):**
  - O tema muda na hora; a escolha pode ser lembrada ao recarregar (a confirmar).
- [ ] passou

### TD03. Admin: aba de gráficos em tempo real *(a confirmar)*

- **Objetivo:** acompanhar métricas ao vivo.
- **Passos:**
  1. Abra a **aba de gráficos** do admin com música tocando.
  2. Toque, pule, faça buscas e comandos.
- **Esperado (a confirmar):**
  - Gráficos **em tempo real** agrupados por **seção** (por exemplo áudio, fila, comandos, buscas), que se movem conforme o uso, sem recarregar a página.
- [ ] passou

### TD04. Admin: ouvir a música pela interface *(a confirmar)*

- **Objetivo:** escutar no navegador o que o bot está tocando.
- **Passos:**
  1. Com música tocando, use o controle de ouvir da interface do admin.
- **Esperado (a confirmar):**
  - O áudio toca no navegador, acompanhando a música do servidor; dá para ligar e desligar sem afetar o canal de voz.
- [ ] passou

---

## Resumo dos resultados

Preencha **OK**, **FALHOU** ou **N/A** e anote observações. Data do teste: ____ / ____ / ________ · Versão/commit: ____________

| ID | Área | Teste | Resultado |
|---|---|---|---|
| T01 | Reprodução | `/play` por busca | |
| T02 | Reprodução | `/play` por URL | |
| T03 | Reprodução | Autocomplete 🔎 | |
| T04 | Reprodução | `/kassino` | |
| T05 | Reprodução | Tocar fora do canal de voz (erro efêmero) | |
| T06 | Reprodução | Segunda música entra na fila | |
| T07 | Painel | Painel aparece completo | |
| T08 | Painel | ⏸️ Pausar / ▶️ Continuar | |
| T09 | Painel | ⏭️ Pular | |
| T10 | Painel | 🔀 Embaralhar | |
| T11 | Painel | 🔁 Loop (3 modos) | |
| T12 | Painel | 🔉 / 🔊 volume | |
| T13 | Painel | 📜 Fila | |
| T14 | Painel | ⏹️ Parar | |
| T15 | Painel | Atualização automática por comandos | |
| T16 | Painel | Barra de progresso a cada ~15 s | |
| T17 | Painel | KB transmitidos | |
| T18 | Painel | `/player` traz o painel de volta | |
| T19 | Fila | Fila vazia | |
| T20 | Fila | `/queue` com paginação | |
| T21 | Fila | Remover pelo select | |
| T22 | Fila | Limpar fila | |
| T23 | Playlists | Sem playlists ainda | |
| T24 | Playlists | Criar (servidor) | |
| T25 | Playlists | Criar (global) | |
| T26 | Playlists | Nome duplicado | |
| T27 | Playlists | Listar | |
| T28 | Playlists | Adicionar por busca + apelido | |
| T29 | Playlists | Adicionar por URL | |
| T30 | Playlists | Ver playlist + botões | |
| T31 | Playlists | Tocar playlist | |
| T32 | Playlists | Tocar em ordem aleatória | |
| T33 | Playlists | Salvar a música atual | |
| T34 | Playlists | Apelido (definir/remover) e ⭐ no `/play` | |
| T35 | Playlists | Remover música | |
| T36 | Playlists | Mover música levando o apelido | |
| T37 | Playlists | Renomear | |
| T38 | Playlists | Apagar com confirmação | |
| T39 | Playlists | Visibilidade entre servidores | |
| T40 | Playlists | Outra pessoa não altera | |
| T41 | Busca | `/search` mostra 5 resultados | |
| T42 | Busca | Botão ▶️ | |
| T43 | Busca | 💾 Salvar (modal playlist + apelido) | |
| T44 | Prioridade | Playlist e depois `/play` avulso | |
| T45 | Prioridade | Avulsas e depois playlist | |
| T46 | Prioridade | Ordem real de execução | |
| T47 | Controles | `/pause` | |
| T48 | Controles | `/skip` | |
| T49 | Controles | `/volume` 0 a 150 | |
| T50 | Controles | `/stop` sai do canal | |
| T51 | Localização | Cliente em pt-BR | |
| T52 | Localização | Cliente em inglês | |
| T53 | Erros | URL inválida | |
| T54 | Erros | Busca sem resultado | |
| T55 | Erros | Controle sem nada tocando | |
| T56 | Erros | Playlist vazia | |
| T57 | Admin | Login e logout | |
| T58 | Admin | Servidores ao vivo | |
| T59 | Admin | Pausar/pular/parar e o Discord acompanha | |
| T60 | Admin | Página do servidor: controles | |
| T61 | Admin | Fila: subir/descer/remover/limpar | |
| T62 | Admin | Mover entre grupos (toast de erro) | |
| T63 | Admin | Tocar música pelo admin | |
| T64 | Admin | Tocar playlist pelo admin | |
| T65 | Admin | Playlists: lista e filtros | |
| T66 | Admin | Criar playlist | |
| T67 | Admin | Renomear, adicionar música e apelidar | |
| T68 | Admin | Mover e remover música | |
| T69 | Admin | Apagar playlist | |
| T70 | Admin | Erros (toast e mensagem) | |
| T71 | Métricas | `/actuator/prometheus` com senha | |
| T72 | Métricas | Métricas `sabadaco_*` | |
| T73 | Docker | Restart perde playlists (esperado) | |
| T74 | Ajuda | `/help` lista por categoria | |
| T75 | Ajuda | `/help` detalhes e exemplos pelo select | |
| T76 | Ajuda | `/help` botão voltar | |
| T77 | Ajuda | `/help comando:<nome>` direto | |
| TD01 | Em desenvolvimento | Admin: temas escuro e creme claro | |
| TD02 | Em desenvolvimento | Admin: alternar tema | |
| TD03 | Em desenvolvimento | Admin: gráficos em tempo real | |
| TD04 | Em desenvolvimento | Admin: ouvir a música pela UI | |
