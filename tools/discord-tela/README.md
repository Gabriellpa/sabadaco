# sabadaco-tela

*Made by a friend, for friends, to share joy.*

Libera **compartilhar a tela dentro da Atividade do Sabadaço** no app do Discord para Windows. Só quem vai
transmitir precisa instalar. Quem assiste não instala nada.

Sem ele, o botão **📡 Transmitir** da Atividade abre o navegador para transmitir, como antes.

> ⚠️ Isto modifica o app do Discord no seu PC. Isso vai contra os Termos de Serviço do Discord, e o risco é de
> quem instala. Use só se topar.

## Instalar

1. Baixe `sabadaco-tela.ps1`.
2. No PowerShell, na pasta do arquivo:
   ```powershell
   powershell -ExecutionPolicy Bypass -File .\sabadaco-tela.ps1
   ```
   O script mostra a pasta e as versões do Discord que encontrou e pergunta antes de mexer (`S` para seguir).
   Depois fecha o Discord, instala e abre o Discord de novo. Com `-Sim`, não pergunta.
3. Abra a Atividade do Sabadaço, clique em **📡 Transmitir**, dê um título e depois clique em
   **Compartilhar tela**. Uma janela mostra as telas e janelas com prévia ao vivo. Escolha uma e clique em
   **Transmitir** (ou clique duas vezes nela).

Quando o Discord se atualizar, a instalação some. É só rodar o arquivo de novo.

## Remover

```powershell
powershell -ExecutionPolicy Bypass -File .\sabadaco-tela.ps1 -Remover
```

O script acha o Discord sozinho: primeiro pelo Discord aberto, depois nas pastas padrão (Discord, PTB, Canary). Se
mesmo assim não achar, passe a pasta que tem as `app-*`: `-Pasta "D:\Apps\Discord"`.

## O que ele faz

O Discord bloqueia a captura de tela (e a tela cheia) nas Atividades em três pontos. O arquivo libera os três **só para a
Atividade do Sabadaço** (`888603239304527884.discordsays.com`). Nenhum outro site ou Atividade ganha nada.

1. **Permissão do iframe:** acrescenta `display-capture; camera; microphone; fullscreen` ao `allow` do iframe da
   nossa Atividade antes de ele carregar.
2. **Permissão do app:** embrulha os handlers de permissão do Discord para dizer "sim" a `media`,
   `display-capture` e `fullscreen` só quando o pedido vem da nossa Atividade. O resto continua decidido pelo Discord.
3. **Seletor de tela:** o Electron não tem o seletor do Chrome, então o arquivo abre uma janela própria, no visual
   do Sabadaço, com abas Telas e Janelas e miniaturas que se atualizam enquanto ela está aberta. Se a página pediu
   áudio, a opção **Incluir o som do computador** manda o áudio do sistema (só no Windows).

O cursor do mouse aparece na transmissão quando ele está sobre a tela ou janela escolhida. É o mesmo comportamento
do Chrome e do Discord: o Electron não tem opção para esconder.

A instalação copia `sabadaco-tela.js` para o módulo `discord_desktop_core` do Discord (o mesmo lugar em que
mods como o BetterDiscord se instalam) e acrescenta uma linha `require` no `index.js` dele.

A página da Atividade percebe sozinha que o `display-capture` foi liberado e compartilha ali mesmo. Se o app
recusar (por exemplo, depois de uma atualização do Discord), ela volta a abrir o navegador.

## Problemas

O log fica em `%APPDATA%\discord\sabadaco-tela.log`:

- `sabadaco-tela ativo`: o arquivo carregou.
- `seletor: window "Nome"` (ou `screen`): o que foi escolhido para transmitir.
- `handler de permissão do Discord embrulhado`: o passo 2 pegou. Se essa linha não aparecer e a Atividade der
  `NotAllowedError`, o Discord registrou as permissões antes do arquivo carregar: avise no projeto.
