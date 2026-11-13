<#
.SYNOPSIS
    Libera compartilhar a tela dentro da Atividade do Sabadaço no app do Discord para Windows.

.DESCRIPTION
    O Discord bloqueia captura de tela nas Atividades. Este script instala um arquivo pequeno no app do Discord
    (no módulo discord_desktop_core, o mesmo lugar onde mods como o BetterDiscord se instalam) que:
      1. acrescenta display-capture/camera/microphone ao "allow" do iframe SÓ da Atividade do Sabadaço;
      2. libera a permissão de mídia SÓ para a origem da Atividade do Sabadaço;
      3. mostra um seletor de tela ou janela quando a Atividade pede para compartilhar.
    Nenhum outro site ou Atividade ganha nada. Atualizações do Discord desfazem a instalação: rode de novo.

    ATENÇÃO: modificar o app vai contra os Termos de Serviço do Discord. Use por sua conta.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File .\sabadaco-tela.ps1
    powershell -ExecutionPolicy Bypass -File .\sabadaco-tela.ps1 -Remover
#>
[CmdletBinding(PositionalBinding = $false)]
param(
    [switch]$Remover,
    # Pasta do Discord (a que tem as app-*). Vazio = acha sozinho: o Discord aberto, ou a pasta padrão.
    [string]$Pasta = ''
)

$ErrorActionPreference = 'Stop'
$Marcador = '// sabadaco-tela'
$Arquivo = 'sabadaco-tela.js'

# ---------------------------------------------------------------------------------------------------------------
# Código que roda dentro do app do Discord (processo principal do Electron).
# ---------------------------------------------------------------------------------------------------------------
$Codigo = @'
// sabadaco-tela: compartilhar a tela na Atividade do Sabadaço (e só nela). Instalado por sabadaco-tela.ps1.
'use strict';
(() => {
    const HOST = '888603239304527884.discordsays.com';
    const PERMISSOES = ['media', 'display-capture'];

    let electron;
    try { electron = require('electron'); } catch (e) { return; }
    const { app, session, desktopCapturer, dialog, BrowserWindow, webContents } = electron;
    const fs = require('fs');
    const path = require('path');

    const log = (...partes) => {
        try {
            fs.appendFileSync(path.join(app.getPath('userData'), 'sabadaco-tela.log'),
                new Date().toISOString() + ' ' + partes.join(' ') + '\n');
        } catch (e) { /* sem log */ }
    };
    const daAtividade = (url) => {
        try { return new URL(String(url)).host === HOST; } catch (e) { return false; }
    };
    const origemDe = (details) => details && (details.requestingUrl || details.securityOrigin || details.requestingOrigin);

    // 1) Página do Discord: o iframe da nossa Atividade nasce com display-capture/camera/microphone no "allow"
    //    (precisa ser antes de ele carregar; mudar depois não vale).
    const PATCH_PAGINA = `(() => {
        if (window.__sabadacoTela) return;
        window.__sabadacoTela = true;
        const HOST = ${JSON.stringify(HOST)};
        const EXTRA = ['display-capture', 'camera', 'microphone'];
        const nosso = (src) => { try { return new URL(String(src), location.href).host === HOST; } catch (e) { return false; } };
        const P = HTMLIFrameElement.prototype;
        const allow = Object.getOwnPropertyDescriptor(P, 'allow');
        const src = Object.getOwnPropertyDescriptor(P, 'src');
        const setAttribute = Element.prototype.setAttribute;
        const juntar = (valor) => {
            const s = new Set(String(valor || '').split(';').map((x) => x.trim()).filter(Boolean));
            EXTRA.forEach((p) => s.add(p));
            return [...s].join('; ');
        };
        const ajustar = (iframe, url) => { if (nosso(url)) allow.set.call(iframe, juntar(allow.get.call(iframe))); };
        Element.prototype.setAttribute = function (nome, valor) {
            const r = setAttribute.call(this, nome, valor);
            if (this instanceof HTMLIFrameElement) {
                const n = String(nome).toLowerCase();
                if (n === 'src') ajustar(this, valor);
                else if (n === 'allow') ajustar(this, this.getAttribute('src'));
            }
            return r;
        };
        Object.defineProperty(P, 'allow', { configurable: true, enumerable: true,
            get() { return allow.get.call(this); },
            set(v) { allow.set.call(this, nosso(this.getAttribute('src')) ? juntar(v) : v); } });
        Object.defineProperty(P, 'src', { configurable: true, enumerable: true,
            get() { return src.get.call(this); },
            set(v) { ajustar(this, v); src.set.call(this, v); } });
    })();`;

    const ehDiscord = (url) => /^https:\/\/([a-z]+\.)?discord\.com\//.test(String(url));
    const prepararPagina = (wc) => {
        wc.on('dom-ready', () => {
            if (ehDiscord(wc.getURL())) wc.executeJavaScript(PATCH_PAGINA).catch((e) => log('patch da página falhou:', e.message));
        });
    };

    // 2) Permissões: o Discord registra os próprios handlers; embrulhamos para liberar mídia só para a nossa origem.
    function embrulharPermissoes(proto) {
        const setRequest = proto.setPermissionRequestHandler;
        proto.setPermissionRequestHandler = function (handler) {
            log('handler de permissão do Discord embrulhado');
            return setRequest.call(this, handler && function (wc, permissao, callback, details) {
                if (PERMISSOES.includes(permissao) && daAtividade(origemDe(details))) return callback(true);
                return handler.apply(this, arguments);
            });
        };
        const setCheck = proto.setPermissionCheckHandler;
        proto.setPermissionCheckHandler = function (handler) {
            return setCheck.call(this, handler && function (wc, permissao, origem, details) {
                if (PERMISSOES.includes(permissao) && (daAtividade(origem) || daAtividade(origemDe(details)))) return true;
                return handler.apply(this, arguments);
            });
        };
        // Se o Discord um dia registrar o próprio handler de captura, o nosso continua valendo para a Atividade
        const setDisplay = proto.setDisplayMediaRequestHandler;
        proto.setDisplayMediaRequestHandler = function (handler, opts) {
            return setDisplay.call(this, handler && function (request, callback) {
                if (pedidoDaAtividade(request)) return escolherTela(request, callback);
                return handler.apply(this, arguments);
            }, opts);
        };
        return setDisplay;
    }

    const pedidoDaAtividade = (request) => daAtividade(request.securityOrigin)
        || daAtividade(request.frame && (request.frame.origin || request.frame.url));

    // 3) Seletor de tela ou janela (o Electron não tem o do Chrome)
    async function escolherTela(request, callback) {
        try {
            const fontes = (await desktopCapturer.getSources({ types: ['screen', 'window'], thumbnailSize: { width: 0, height: 0 } }))
                .slice(0, 15);
            const nomes = fontes.map((f, i) => (f.id.startsWith('screen:') ? '🖥 Tela ' + (i + 1) : '🗔 ' + f.name).slice(0, 60));
            const janela = BrowserWindow.getFocusedWindow() || BrowserWindow.getAllWindows()[0];
            const botoes = nomes.concat('Cancelar');
            const { response } = await dialog.showMessageBox(janela, {
                type: 'question', title: 'Sabadaço', message: 'O que você quer transmitir?',
                buttons: botoes, cancelId: botoes.length - 1, noLink: true,
            });
            if (response >= fontes.length) return callback({});
            const escolha = { video: fontes[response] };
            if (request.audioRequested && process.platform === 'win32') escolha.audio = 'loopback';
            callback(escolha);
        } catch (e) {
            log('seletor falhou:', e.message);
            try { callback({}); } catch (e2) { /* já respondido */ }
        }
    }

    function iniciar() {
        try {
            const proto = Object.getPrototypeOf(session.defaultSession);
            const setDisplay = embrulharPermissoes(proto);
            // Hoje o Discord não tem handler de captura: registramos um que só atende a Atividade do Sabadaço
            setDisplay.call(session.defaultSession, (request, callback) => {
                if (pedidoDaAtividade(request)) return escolherTela(request, callback);
                callback({});
            });
            app.on('web-contents-created', (_, wc) => prepararPagina(wc));
            webContents.getAllWebContents().forEach(prepararPagina);
            log('sabadaco-tela ativo');
        } catch (e) {
            log('falhou ao iniciar:', e.stack || e.message);
        }
    }

    if (app.isReady()) iniciar(); else app.once('ready', iniciar);
})();
'@

# ---------------------------------------------------------------------------------------------------------------

function Find-PastaDiscord {
    # 1) O Discord aberto: ...\<Pasta>\app-1.0.XXXX\Discord.exe (qualquer nome de pasta)
    foreach ($nome in 'Discord', 'DiscordPTB', 'DiscordCanary') {
        $exe = Get-Process -Name $nome -ErrorAction SilentlyContinue | Where-Object { $_.Path } |
            Select-Object -First 1 -ExpandProperty Path
        if ($exe) {
            $app = Split-Path $exe -Parent
            if ((Split-Path $app -Leaf) -like 'app-*') { return (Split-Path $app -Parent) }
        }
    }
    # 2) As pastas padrão do instalador
    foreach ($nome in 'Discord', 'DiscordPTB', 'DiscordCanary') {
        $p = Join-Path $env:LOCALAPPDATA $nome
        if (Get-ChildItem $p -Directory -Filter 'app-*' -ErrorAction SilentlyContinue) { return $p }
    }
    throw "Não achei o Discord. Abra o Discord e rode de novo, ou passe a pasta: -Pasta 'C:\...\Discord' (a que tem as pastas app-*)."
}

# Todas as versões instaladas (app-*): o Discord pode abrir uma ou outra, então instala em todas
function Get-PastasCore {
    if (-not (Test-Path $Pasta)) { throw "Discord não encontrado em $Pasta" }
    $dirs = foreach ($app in Get-ChildItem $Pasta -Directory -Filter 'app-*') {
        $core = Get-ChildItem (Join-Path $app.FullName 'modules') -Directory -Filter 'discord_desktop_core-*' -ErrorAction SilentlyContinue |
            Sort-Object { [int]($_.Name -replace '^discord_desktop_core-', '') } -Descending | Select-Object -First 1
        if ($core) {
            $dir = Join-Path $core.FullName 'discord_desktop_core'
            if (Test-Path (Join-Path $dir 'index.js')) { $dir }
        }
    }
    if (-not $dirs) { throw "Nenhuma versão do Discord (app-*\modules\discord_desktop_core-*) em $Pasta" }
    return @($dirs)
}

# Nome do executável (Discord, DiscordPTB, DiscordCanary), pela versão mais nova
function Get-NomeExe {
    $exe = Get-ChildItem $Pasta -Directory -Filter 'app-*' | Sort-Object Name -Descending |
        ForEach-Object { Get-ChildItem $_.FullName -File -Filter 'Discord*.exe' } | Select-Object -First 1
    if ($exe) { return $exe.BaseName } else { return 'Discord' }
}

function Stop-Discord {
    $proc = Get-Process -Name $NomeExe -ErrorAction SilentlyContinue
    if ($proc) {
        Write-Host "Fechando o $NomeExe..."
        $proc | Stop-Process -Force
        Start-Sleep -Seconds 2
    }
}

function Start-Discord {
    $update = Join-Path $Pasta 'Update.exe'
    if (Test-Path $update) { Start-Process $update -ArgumentList '--processStart', "$NomeExe.exe" }
    else { Write-Host 'Abra o Discord de novo.' }
}

$utf8 = New-Object System.Text.UTF8Encoding $false
if (-not $Pasta) { $Pasta = Find-PastaDiscord }
$NomeExe = Get-NomeExe
Write-Host "Discord em $Pasta ($NomeExe)"
$dirs = Get-PastasCore

Stop-Discord
foreach ($dir in $dirs) {
    $index = Join-Path $dir 'index.js'
    $js = Join-Path $dir $Arquivo
    $conteudo = [System.IO.File]::ReadAllText($index)
    if ($Remover) {
        $linhas = $conteudo -split "`r?`n" | Where-Object { $_ -notmatch [regex]::Escape($Marcador) }
        [System.IO.File]::WriteAllText($index, ($linhas -join "`n"), $utf8)
        if (Test-Path $js) { Remove-Item $js }
        Write-Host "removido de $dir"
    } else {
        [System.IO.File]::WriteAllText($js, $Codigo, $utf8)
        if (-not $conteudo.Contains($Marcador)) {
            [System.IO.File]::WriteAllText($index, "require('./$Arquivo'); $Marcador`n" + $conteudo, $utf8)
        }
        Write-Host "instalado em $dir"
    }
}
if ($Remover) {
    Write-Host 'sabadaco-tela removido.' -ForegroundColor Green
} else {
    Write-Host 'sabadaco-tela instalado.' -ForegroundColor Green
    Write-Host 'Quando o Discord atualizar, rode este arquivo de novo.'
}
Start-Discord
