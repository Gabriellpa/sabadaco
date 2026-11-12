# sabadaco-tela

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
   O script fecha o Discord, instala e abre o Discord de novo.
3. Abra a Atividade do Sabadaço, clique em **📡 Transmitir**, dê um título e depois clique em
   **Compartilhar tela**. Uma janela pergunta qual tela ou janela transmitir.

Quando o Discord se atualizar, a instalação some. É só rodar o arquivo de novo.

## Remover

```powershell
powershell -ExecutionPolicy Bypass -File .\sabadaco-tela.ps1 -Remover
```

Para o Discord PTB ou Canary, passe a pasta: `-Pasta "$env:LOCALAPPDATA\DiscordPTB"`.

## O que ele faz

O Discord bloqueia a captura de tela nas Atividades em três pontos. O arquivo libera os três **só para a
Atividade do Sabadaço** (`888603239304527884.discordsays.com`). Nenhum outro site ou Atividade ganha nada.

1. **Permissão do iframe:** acrescenta `display-capture; camera; microphone` ao `allow` do iframe da nossa
   Atividade antes de ele carregar.
2. **Permissão de mídia do app:** embrulha os handlers de permissão do Discord para dizer "sim" a `media` e
   `display-capture` só quando o pedido vem da nossa Atividade. O resto continua decidido pelo Discord.
3. **Seletor de tela:** o Electron não tem o seletor do Chrome, então um diálogo simples lista telas e janelas.
   Com o switch **Áudio** ligado, no Windows ele também manda o áudio do sistema.

A instalação copia `sabadaco-tela.js` para o módulo `discord_desktop_core` do Discord (o mesmo lugar em que
mods como o BetterDiscord se instalam) e acrescenta uma linha `require` no `index.js` dele.

A página da Atividade percebe sozinha que o `display-capture` foi liberado e compartilha ali mesmo. Se o app
recusar (por exemplo, depois de uma atualização do Discord), ela volta a abrir o navegador.

## Problemas

O log fica em `%APPDATA%\discord\sabadaco-tela.log`:

- `sabadaco-tela ativo`: o arquivo carregou.
- `handler de permissão do Discord embrulhado`: o passo 2 pegou. Se essa linha não aparecer e a Atividade der
  `NotAllowedError`, o Discord registrou as permissões antes do arquivo carregar: avise no projeto.
