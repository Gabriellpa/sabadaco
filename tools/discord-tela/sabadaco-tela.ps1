<#
    Made by a friend, for friends, to share joy.

.SYNOPSIS
    Libera compartilhar a tela dentro da Atividade do Sabadaço no app do Discord para Windows.

.DESCRIPTION
    O Discord bloqueia captura de tela nas Atividades. Este script instala um arquivo pequeno no app do Discord
    (no módulo discord_desktop_core, o mesmo lugar onde mods como o BetterDiscord se instalam) que:
      1. acrescenta display-capture/camera/microphone/fullscreen ao "allow" do iframe SÓ da Atividade do Sabadaço;
      2. libera a permissão de mídia e de tela cheia SÓ para a origem da Atividade do Sabadaço;
      3. mostra um seletor de tela ou janela (com prévia ao vivo) quando a Atividade pede para compartilhar.
    Nenhum outro site ou Atividade ganha nada. Atualizações do Discord desfazem a instalação: rode de novo.

    ATENÇÃO: modificar o app vai contra os Termos de Serviço do Discord. Use por sua conta.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File .\sabadaco-tela.ps1
    powershell -ExecutionPolicy Bypass -File .\sabadaco-tela.ps1 -Remover
#>
[CmdletBinding(PositionalBinding = $false)]
param(
    [switch]$Remover,
    # Não pergunta antes de instalar/remover
    [switch]$Sim,
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
// Made by a friend, for friends, to share joy.
'use strict';
(() => {
    const HOST = '888603239304527884.discordsays.com';
    const PERMISSOES = ['media', 'display-capture', 'fullscreen'];

    let electron;
    try { electron = require('electron'); } catch (e) { return; }
    const { app, session, desktopCapturer, BrowserWindow, webContents } = electron;
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

    // 1) Página do Discord: o iframe da nossa Atividade nasce com display-capture/camera/microphone/fullscreen no "allow"
    //    (precisa ser antes de ele carregar; mudar depois não vale).
    const PATCH_PAGINA = `(() => {
        if (window.__sabadacoTela) return;
        window.__sabadacoTela = true;
        const HOST = ${JSON.stringify(HOST)};
        const EXTRA = ['display-capture', 'camera', 'microphone', 'fullscreen'];
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

    // 3) Seletor de tela ou janela (o Electron não tem o do Chrome): janela própria, com miniaturas ao vivo.
    const SELETOR_HTML = `<!doctype html>
<html lang="pt-BR"><head><meta charset="utf-8">
<meta http-equiv="Content-Security-Policy" content="default-src 'none'; img-src data:; style-src 'unsafe-inline'; script-src 'unsafe-inline'">
<title>Sabadaço · Transmitir</title>
<style>
:root { color-scheme: dark; --bg: #07080b; --surface: #16171d; --line: rgba(255,255,255,.08); --text: #f5f3ee; --muted: #8d8a82;
  --accent: #ff8a3d; --accent-2: #ffb070; --accent-3: #ff5e7e; --ink: #1c1206; }
* { box-sizing: border-box; }
[hidden] { display: none !important; }
html, body { margin: 0; height: 100%; }
body { font: 14px/1.4 "Segoe UI Variable", "Segoe UI", system-ui, sans-serif; color: var(--text); background: var(--bg);
  display: flex; flex-direction: column; overflow: hidden; border: 1px solid var(--line); border-radius: 14px; user-select: none; }
body::before { content: ""; position: fixed; inset: -30% -10% auto; height: 70%; pointer-events: none; z-index: -1;
  background: radial-gradient(40% 60% at 20% 30%, rgba(255,138,61,.20), transparent 70%), radial-gradient(35% 55% at 80% 20%, rgba(255,94,126,.16), transparent 70%); }
header { display: flex; align-items: center; gap: 14px; padding: 18px 20px 6px; -webkit-app-region: drag; }
.logo { width: 34px; height: 34px; border-radius: 10px; display: grid; place-items: center; font-weight: 800; color: var(--ink);
  background: linear-gradient(135deg, var(--accent-2), var(--accent) 55%, var(--accent-3)); box-shadow: 0 6px 20px -6px rgba(255,138,61,.7); }
h1 { margin: 0; font-size: 17px; font-weight: 700; letter-spacing: -.01em; }
header p { margin: 1px 0 0; color: var(--muted); font-size: 12.5px; }
.close { margin-left: auto; -webkit-app-region: no-drag; }
nav { display: flex; gap: 6px; padding: 10px 20px 12px; }
.tab { -webkit-app-region: no-drag; border: 1px solid var(--line); background: rgba(255,255,255,.04); color: var(--muted);
  padding: 7px 14px; border-radius: 999px; font: inherit; font-weight: 600; cursor: pointer; transition: .2s; }
.tab:hover { color: var(--text); }
.tab[aria-selected="true"] { color: var(--ink); background: linear-gradient(135deg, var(--accent-2), var(--accent)); border-color: transparent; }
.tab span { opacity: .7; font-weight: 500; margin-left: 4px; }
main { flex: 1; overflow: auto; padding: 4px 20px 16px; }
main::-webkit-scrollbar { width: 10px; } main::-webkit-scrollbar-thumb { background: rgba(255,255,255,.12); border-radius: 10px; border: 3px solid var(--bg); }
.grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(250px, 1fr)); gap: 14px; }
.card { border: 1px solid var(--line); background: var(--surface); border-radius: 14px; padding: 8px; cursor: pointer; text-align: left;
  color: inherit; font: inherit; transition: transform .2s, box-shadow .2s, border-color .2s; }
.card:hover { transform: translateY(-2px); border-color: rgba(255,255,255,.18); }
.card[aria-checked="true"] { border-color: transparent; box-shadow: 0 0 0 2px var(--accent), 0 14px 40px -14px rgba(255,138,61,.65); }
.thumb { aspect-ratio: 16 / 9; border-radius: 9px; background: #000 center / contain no-repeat; position: relative; overflow: hidden; }
.thumb.vazio::after { content: "sem prévia"; position: absolute; inset: 0; display: grid; place-items: center; color: var(--muted); font-size: 12px; }
.live { position: absolute; top: 7px; left: 7px; font-size: 10.5px; font-weight: 700; letter-spacing: .06em; padding: 2px 7px; border-radius: 999px;
  background: rgba(0,0,0,.55); color: #fff; display: none; }
.card[aria-checked="true"] .live { display: block; }
.live::before { content: ""; display: inline-block; width: 6px; height: 6px; border-radius: 50%; background: #ff4d5e; margin-right: 5px; vertical-align: 1px; }
.name { display: flex; align-items: center; gap: 8px; padding: 9px 4px 3px; min-width: 0; }
.name img { width: 18px; height: 18px; flex: none; }
.name b { font-weight: 600; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.empty { color: var(--muted); padding: 40px 0; text-align: center; }
footer { display: flex; align-items: center; gap: 10px; padding: 14px 20px; border-top: 1px solid var(--line); background: rgba(22,23,29,.7); }
.audio { display: flex; align-items: center; gap: 9px; color: var(--muted); cursor: pointer; }
.audio input { appearance: none; width: 34px; height: 20px; border-radius: 999px; background: rgba(255,255,255,.14); position: relative; cursor: pointer; transition: .2s; margin: 0; }
.audio input::after { content: ""; position: absolute; top: 3px; left: 3px; width: 14px; height: 14px; border-radius: 50%; background: #fff; transition: .2s; }
.audio input:checked { background: var(--accent); } .audio input:checked::after { left: 17px; }
.audio:has(input:checked) { color: var(--text); }
.spacer { flex: 1; }
button.btn { font: inherit; font-weight: 600; border-radius: 10px; padding: 9px 18px; cursor: pointer; border: 1px solid var(--line); transition: .2s; }
.ghost { background: transparent; color: var(--text); } .ghost:hover { background: rgba(255,255,255,.06); }
.primary { border: 0; color: var(--ink); background: linear-gradient(135deg, var(--accent-2), var(--accent) 55%, var(--accent-3)); box-shadow: 0 8px 26px -10px rgba(255,138,61,.8); }
.primary:disabled { opacity: .4; cursor: default; box-shadow: none; }
.icon { width: 32px; height: 32px; padding: 0; display: grid; place-items: center; font-size: 15px; }
@media (prefers-reduced-motion: reduce) { * { transition: none !important; } }
</style></head>
<body>
<header><div class="logo">S</div><div><h1>O que você quer transmitir?</h1><p>A prévia atualiza sozinha. Clique duas vezes para transmitir.</p></div>
<button class="btn ghost icon close" id="fechar" title="Cancelar (Esc)">✕</button></header>
<nav role="tablist"><button class="tab" id="tab-screen" role="tab">Telas<span></span></button><button class="tab" id="tab-window" role="tab">Janelas<span></span></button></nav>
<main><div class="grid" id="grade" role="radiogroup"></div><div class="empty" id="vazio" hidden>Nada para mostrar aqui.</div></main>
<footer><label class="audio" id="audio-box" hidden><input type="checkbox" id="audio" checked> Incluir o som do computador</label>
<div class="spacer"></div><button class="btn ghost" id="cancelar">Cancelar</button><button class="btn primary" id="ok" disabled>Transmitir</button></footer>
<script>
(() => {
  const $ = (id) => document.getElementById(id);
  let fontes = [], aba = 'window', escolhida = null, responder;
  window.__resultado = new Promise((r) => { responder = r; });
  const fim = (valor) => responder(valor);
  const enviar = () => { if (escolhida) fim({ id: escolhida, audio: !$('audio-box').hidden && $('audio').checked }); };
  const card = (f) => {
    const b = document.createElement('button');
    b.className = 'card'; b.type = 'button'; b.setAttribute('role', 'radio'); b.dataset.id = f.id;
    const t = document.createElement('div'); t.className = 'thumb';
    const live = document.createElement('span'); live.className = 'live'; live.textContent = 'ESCOLHIDA';
    t.append(live);
    const n = document.createElement('div'); n.className = 'name';
    const nome = document.createElement('b');
    n.append(nome); b.append(t, n);
    b.addEventListener('click', () => { escolhida = f.id; desenhar(); });
    b.addEventListener('dblclick', () => { escolhida = f.id; enviar(); });
    return b;
  };
  const atualizarCard = (b, f) => {
    const t = b.firstChild;
    t.style.backgroundImage = f.thumb ? 'url("' + f.thumb + '")' : '';
    t.classList.toggle('vazio', !f.thumb);
    const n = b.lastChild;
    let icone = n.querySelector('img');
    if (f.icon && !icone) { icone = document.createElement('img'); icone.alt = ''; n.prepend(icone); }
    if (icone) { if (f.icon) icone.src = f.icon; else icone.remove(); }
    n.querySelector('b').textContent = f.name;
    n.title = f.name;
    b.setAttribute('aria-checked', String(f.id === escolhida));
  };
  function desenhar() {
    const lista = fontes.filter((f) => f.kind === aba);
    const grade = $('grade');
    const atuais = new Map([...grade.children].map((b) => [b.dataset.id, b]));
    const nova = lista.map((f) => { const b = atuais.get(f.id) || card(f); atualizarCard(b, f); return b; });
    grade.replaceChildren(...nova);
    $('vazio').hidden = lista.length > 0;
    for (const k of ['screen', 'window']) {
      const tab = $('tab-' + k);
      tab.setAttribute('aria-selected', String(k === aba));
      tab.querySelector('span').textContent = String(fontes.filter((f) => f.kind === k).length);
    }
    $('ok').disabled = !fontes.some((f) => f.id === escolhida);
  }
  window.__render = (dados) => {
    const primeira = fontes.length === 0;
    fontes = dados.sources;
    $('audio-box').hidden = !dados.audio;
    if (primeira && !fontes.some((f) => f.kind === 'window')) aba = 'screen';
    if (escolhida && !fontes.some((f) => f.id === escolhida)) escolhida = null;
    desenhar();
  };
  for (const k of ['screen', 'window']) $('tab-' + k).addEventListener('click', () => { aba = k; desenhar(); });
  $('ok').addEventListener('click', enviar);
  $('cancelar').addEventListener('click', () => fim(null));
  $('fechar').addEventListener('click', () => fim(null));
  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') fim(null);
    else if (e.key === 'Enter') enviar();
  });
})();
</script></body></html>`;

    const MINIATURA = { width: 384, height: 216 };
    const fontesComPrevia = async () => {
        const fontes = await desktopCapturer.getSources({ types: ['screen', 'window'], thumbnailSize: MINIATURA, fetchWindowIcons: true });
        const telas = fontes.filter((f) => f.id.startsWith('screen:'));
        return fontes.map((f) => {
            const tela = f.id.startsWith('screen:');
            const previa = f.thumbnail && !f.thumbnail.isEmpty() ? 'data:image/jpeg;base64,' + f.thumbnail.toJPEG(72).toString('base64') : '';
            return {
                fonte: f,
                dados: {
                    id: f.id, kind: tela ? 'screen' : 'window', thumb: previa,
                    icon: f.appIcon && !f.appIcon.isEmpty() ? f.appIcon.toDataURL() : '',
                    name: tela ? (telas.length > 1 ? 'Tela ' + (telas.indexOf(f) + 1) : 'Tela inteira') : f.name,
                },
            };
        });
    };

    async function escolherTela(request, callback) {
        let respondido = false;
        const responder = (valor) => {
            if (respondido) return;
            respondido = true;
            try { callback(valor); } catch (e) { log('resposta da captura falhou:', e.message); }
        };
        let janela, timer;
        try {
            const audio = !!request.audioRequested && process.platform === 'win32';
            let atuais = await fontesComPrevia();
            const dono = BrowserWindow.getFocusedWindow() || BrowserWindow.getAllWindows()[0];
            janela = new BrowserWindow({
                parent: dono, modal: !!dono, width: 920, height: 640, minWidth: 560, minHeight: 420, show: false,
                frame: false, backgroundColor: '#07080b', title: 'Sabadaço', autoHideMenuBar: true,
                webPreferences: { partition: 'sabadaco-seletor', sandbox: true, contextIsolation: true, nodeIntegration: false },
            });
            janela.on('closed', () => { clearInterval(timer); responder({}); });
            const wc = janela.webContents;
            wc.setWindowOpenHandler(() => ({ action: 'deny' }));
            wc.on('will-navigate', (e) => e.preventDefault());
            await janela.loadURL('data:text/html;charset=utf-8,' + encodeURIComponent(SELETOR_HTML));
            const render = () => wc.executeJavaScript('window.__render(' + JSON.stringify({ audio, sources: atuais.map((f) => f.dados) }) + ')');
            await render();
            janela.show();
            // Prévia ao vivo: novas miniaturas (e janelas abertas/fechadas) enquanto o seletor está aberto
            let ocupado = false;
            timer = setInterval(async () => {
                if (ocupado || janela.isDestroyed()) return;
                ocupado = true;
                try { atuais = await fontesComPrevia(); if (!janela.isDestroyed()) await render(); }
                catch (e) { /* janela fechando */ } finally { ocupado = false; }
            }, 1200);
            const escolha = await wc.executeJavaScript('window.__resultado');
            clearInterval(timer);
            const fonte = escolha && atuais.find((f) => f.dados.id === escolha.id);
            if (!fonte) { log('seletor: cancelado'); responder({}); }
            else {
                log('seletor: ' + fonte.dados.kind + ' "' + fonte.dados.name + '"' + (escolha.audio ? ' com som' : ''));
                const resposta = { video: fonte.fonte };
                if (escolha.audio) resposta.audio = 'loopback';
                responder(resposta);
            }
        } catch (e) {
            log('seletor falhou:', e.message);
            responder({});
        } finally {
            clearInterval(timer);
            if (janela && !janela.isDestroyed()) janela.close();
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

Write-Host ''
Write-Host "Discord encontrado em: $Pasta"
Write-Host 'Versões:'
$dirs | ForEach-Object { Write-Host ('  ' + ($_ -replace '^.*\\(app-[^\\]+)\\.*$', '$1')) }
Write-Host 'O Discord será fechado e aberto de novo.'
if (-not $Sim) {
    $acao = if ($Remover) { 'Remover o sabadaco-tela' } else { 'Instalar o sabadaco-tela (vai contra os termos do Discord; o risco é seu)' }
    $resposta = Read-Host "$acao? (S/N)"
    if ($resposta -notmatch '^[sSyY]') { Write-Host 'Nada foi alterado.'; exit 1 }
}

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
