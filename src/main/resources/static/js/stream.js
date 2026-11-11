/*
 * Página da stream. Fluxo:
 *  1. troca o código (uso único) por um token do LiveKit e uma sessão da página (POST /stream/api/{id}/join);
 *  2. conecta na sala do canal de voz sem assinar nada (autoSubscribe: false) e assina só as telas escolhidas:
 *     a da stream do link e as que a pessoa adicionar com "+" (multistream sem pedir outro código);
 *  3. quem abriu uma stream compartilha a tela em 1080p60 com simulcast (720p30 e 480p15 para redes piores);
 *  4. o chat chega como dado da sala (enviado pelo bot) e sai por POST /stream/api/chat, que posta no Discord.
 * A sessão fica no sessionStorage da aba: com F5 a página pede outro token (POST /stream/api/resume) sem código.
 */
(() => {
    'use strict';

    const LK = window.LivekitClient;
    if (!LK) {
        document.getElementById('app').textContent = 'O cliente de vídeo não carregou. Recarregue a página.';
        return;
    }
    const { Room, RoomEvent, Track, VideoPreset, DisconnectReason } = LK;
    const SCREEN = Track.Source.ScreenShare;
    const SCREEN_AUDIO = Track.Source.ScreenShareAudio;

    const app = document.getElementById('app');
    const streamId = app.dataset.streamId;
    const $ = (id) => document.getElementById(id);

    const SESSION_KEY = 'sabadaco-stream-session:' + streamId;
    const WATCHING_KEY = 'sabadaco-stream-watching:' + streamId;

    /** Captura: 1080p60, com áudio do sistema/aba quando o navegador deixa. */
    const CAPTURE = {
        audio: true,
        systemAudio: 'include',
        resolution: { width: 1920, height: 1080, frameRate: 60 },
        contentHint: 'motion',
        surfaceSwitching: 'include',
        selfBrowserSurface: 'exclude',
    };
    /** Envio: camada cheia de até 6 Mbps e duas menores; o LiveKit entrega a que cabe na rede de cada um. */
    const PUBLISH = {
        videoCodec: 'vp8',
        simulcast: true,
        screenShareEncoding: { maxBitrate: 6_000_000, maxFramerate: 60 },
        screenShareSimulcastLayers: [new VideoPreset(1280, 720, 2_500_000, 30), new VideoPreset(854, 480, 900_000, 15)],
        degradationPreference: 'maintain-framerate',
    };

    const state = {
        room: null,
        session: null,
        identity: null,
        canPublish: false,
        streams: [],
        watching: new Set(),
        tiles: new Map(),
        seenMessages: new Set(),
        switching: false,
        refreshTimer: null,
    };

    // ---------- armazenamento (pode falhar em aba anônima: tudo continua funcionando sem ele) ----------
    const store = {
        get(key) { try { return sessionStorage.getItem(key); } catch { return null; } },
        set(key, value) { try { sessionStorage.setItem(key, value); } catch { /* sem armazenamento */ } },
        remove(key) { try { sessionStorage.removeItem(key); } catch { /* sem armazenamento */ } },
    };

    // ---------- API ----------
    async function api(path, { method = 'GET', body, auth = true } = {}) {
        const headers = { Accept: 'application/json' };
        if (body !== undefined) headers['Content-Type'] = 'application/json';
        if (auth && state.session) headers.Authorization = 'Bearer ' + state.session;
        const response = await fetch('/stream/api' + path, {
            method,
            headers,
            body: body === undefined ? undefined : JSON.stringify(body),
        });
        if (response.status === 204) return null;
        let data = null;
        try { data = await response.json(); } catch { /* sem corpo */ }
        if (!response.ok) {
            const error = new Error((data && data.error) || 'Algo deu errado (' + response.status + ').');
            error.status = response.status;
            throw error;
        }
        return data;
    }

    // ---------- telas ----------
    function show(screen) {
        $('join-screen').hidden = screen !== 'join';
        $('room-screen').hidden = screen !== 'room';
        $('ended-screen').hidden = screen !== 'ended';
    }

    function setStatus(kind, text) {
        $('status').dataset.state = kind;
        $('status-text').textContent = text;
    }

    let toastTimer;
    function toast(text) {
        const el = $('toast');
        el.textContent = text;
        el.hidden = false;
        clearTimeout(toastTimer);
        toastTimer = setTimeout(() => { el.hidden = true; }, 4000);
    }

    function showJoin(error) {
        show('join');
        setStatus('idle', 'Desconectado');
        const message = $('join-error');
        message.hidden = !error;
        message.textContent = error || '';
        const input = $('code');
        if (input) input.focus();
    }

    function showEnded(title, text, canRetry) {
        stopRefresh();
        show('ended');
        setStatus('error', 'Desconectado');
        $('share-toggle').hidden = true;
        $('audio-unlock').hidden = true;
        $('ended-title').textContent = title;
        $('ended-text').textContent = text;
        $('rejoin').hidden = !canRetry;
    }

    // ---------- entrar ----------
    async function joinWithCode(event) {
        event.preventDefault();
        const button = $('join-button');
        const code = $('code').value.trim();
        if (!code) return;
        button.disabled = true;
        button.textContent = 'Entrando…';
        try {
            const ticket = await api('/' + streamId + '/join', { method: 'POST', body: { code }, auth: false });
            await connect(ticket);
        } catch (error) {
            showJoin(error.message);
        } finally {
            button.disabled = false;
            button.textContent = 'Entrar';
        }
    }

    async function resume() {
        try {
            await connect(await api('/resume', { method: 'POST' }));
        } catch (error) {
            if (error.status === 403) {
                forgetSession();
                showJoin(error.message);
            } else {
                showEnded('Não deu para reconectar', error.message, true);
            }
        }
    }

    function forgetSession() {
        state.session = null;
        store.remove(SESSION_KEY);
    }

    async function connect(ticket) {
        state.session = ticket.session;
        state.identity = ticket.identity;
        state.canPublish = ticket.canPublish;
        store.set(SESSION_KEY, ticket.session);
        setStreams(ticket.channelStreams);
        if (state.watching.size === 0) {
            const saved = parseJson(store.get(WATCHING_KEY));
            (Array.isArray(saved) && saved.length ? saved : [ticket.stream.id]).forEach((id) => state.watching.add(id));
        }

        if (state.room) {
            // Trocar de token (passou a poder transmitir): sai sem mostrar a tela de "desconectado"
            state.switching = true;
            await state.room.disconnect();
            clearTiles();
            state.switching = false;
        }

        setStatus('connecting', 'Conectando…');
        const room = new Room({ adaptiveStream: true, dynacast: true });
        wire(room);
        state.room = room;
        await room.connect(ticket.serverUrl, ticket.token, { autoSubscribe: false });

        show('room');
        setStatus('connected', 'Conectado');
        $('room-title').textContent = ticket.stream.title + ' · ' + ticket.stream.hostName;
        $('share-toggle').hidden = !state.canPublish;
        updateShareButton();
        updateAudioButton();
        syncSubscriptions();
        startRefresh();
    }

    function wire(room) {
        const resync = () => { syncSubscriptions(); scheduleRefresh(); };
        room
            .on(RoomEvent.ParticipantConnected, resync)
            .on(RoomEvent.ParticipantDisconnected, (participant) => { removeTile(participant.identity); resync(); })
            .on(RoomEvent.ParticipantMetadataChanged, resync)
            .on(RoomEvent.TrackPublished, resync)
            .on(RoomEvent.TrackUnpublished, (publication, participant) => {
                if (publication.source === SCREEN) removeTile(participant.identity);
                renderStreams();
            })
            .on(RoomEvent.TrackSubscribed, onTrackSubscribed)
            .on(RoomEvent.TrackUnsubscribed, onTrackUnsubscribed)
            .on(RoomEvent.LocalTrackPublished, (publication) => {
                if (publication.source === SCREEN) addLocalTile(publication.track);
                updateShareButton();
                renderStreams();
            })
            .on(RoomEvent.LocalTrackUnpublished, (publication) => {
                if (publication.source === SCREEN) removeTile(state.identity);
                updateShareButton();
                renderStreams();
            })
            .on(RoomEvent.DataReceived, onData)
            .on(RoomEvent.AudioPlaybackStatusChanged, updateAudioButton)
            .on(RoomEvent.Reconnecting, () => setStatus('connecting', 'Reconectando…'))
            .on(RoomEvent.Reconnected, () => setStatus('connected', 'Conectado'))
            .on(RoomEvent.Disconnected, onDisconnected);
    }

    function onDisconnected(reason) {
        if (state.switching) return;
        state.room = null;
        clearTiles();
        if (reason === DisconnectReason.PARTICIPANT_REMOVED) {
            forgetSession();
            showEnded('Você saiu do canal de voz',
                'A stream fecha quando você sai do canal de voz dela. Entre de novo no canal e use /stream join para receber outro código.',
                false);
        } else if (reason === DisconnectReason.DUPLICATE_IDENTITY) {
            showEnded('Aberta em outra aba', 'Você entrou nesta stream em outra aba ou aparelho. Esta aqui foi fechada.', true);
        } else if (reason === DisconnectReason.CLIENT_INITIATED) {
            showEnded('Você saiu da stream', '', true);
        } else {
            showEnded('A conexão caiu', 'Tente reconectar. Se não der, peça outro código com /stream join.', true);
        }
    }

    // ---------- streams do canal e assinaturas ----------
    function setStreams(list) {
        state.streams = list || [];
        // Quem não está mais ao vivo sai da seleção
        const live = new Set(state.streams.map((s) => s.id));
        [...state.watching].forEach((id) => { if (!live.has(id)) state.watching.delete(id); });
        renderStreams();
    }

    function streamOf(participant) {
        return state.streams.find((s) => s.id === participant.metadata)
            || state.streams.find((s) => s.hostId === participant.identity);
    }

    function participantOf(stream) {
        if (!state.room) return null;
        return state.room.remoteParticipants.get(stream.hostId) || null;
    }

    function isSharing(stream) {
        if (stream.hostId === state.identity) {
            return !!(state.room && state.room.localParticipant.isScreenShareEnabled);
        }
        const participant = participantOf(stream);
        return !!(participant && participant.getTrackPublication(SCREEN));
    }

    function syncSubscriptions() {
        if (!state.room) return;
        state.room.remoteParticipants.forEach((participant) => {
            const stream = streamOf(participant);
            const want = !!stream && state.watching.has(stream.id);
            participant.trackPublications.forEach((publication) => {
                if ((publication.source === SCREEN || publication.source === SCREEN_AUDIO) && publication.isSubscribed !== want) {
                    publication.setSubscribed(want);
                }
            });
            if (!want) removeTile(participant.identity);
        });
        renderStreams();
    }

    function toggleWatch(id) {
        if (state.watching.has(id)) state.watching.delete(id);
        else state.watching.add(id);
        store.set(WATCHING_KEY, JSON.stringify([...state.watching]));
        syncSubscriptions();
    }

    function renderStreams() {
        const list = $('stream-list');
        list.replaceChildren();
        if (state.streams.length === 0) {
            const empty = el('li', 'stream-empty', 'Nenhuma stream ao vivo neste canal agora.');
            list.append(empty);
        }
        state.streams.forEach((stream) => {
            const own = stream.hostId === state.identity;
            const watching = own || state.watching.has(stream.id);
            const sharing = isSharing(stream);
            const item = el('li', 'stream-item' + (watching && !own ? ' watching' : ''));
            const info = el('div', 'stream-info');
            info.append(el('div', 'stream-title', stream.title));
            info.append(el('div', 'stream-host', own ? 'Sua stream' : stream.hostName));
            info.append(el('div', 'stream-state' + (sharing ? ' on' : ''), sharing ? '● transmitindo' : 'esperando a tela'));
            item.append(info);
            if (!own) {
                const button = el('button', 'btn btn-sm ' + (watching ? 'btn-ghost' : 'btn-accent'), watching ? '− Tirar' : '+ Assistir');
                button.type = 'button';
                button.title = watching ? 'Parar de assistir esta tela' : 'Adicionar esta tela';
                button.addEventListener('click', () => toggleWatch(stream.id));
                item.append(button);
            }
            list.append(item);
        });
        const channel = state.streams.length ? state.streams.length + (state.streams.length === 1 ? ' stream' : ' streams') : '';
        $('chat-channel').textContent = channel ? '· ' + channel : '';
        updateEmptyText();
    }

    function updateEmptyText() {
        const anyWatching = state.streams.some((s) => s.hostId !== state.identity && state.watching.has(s.id));
        $('stage-empty-text').textContent = anyWatching
            ? 'Esperando quem transmite compartilhar a tela…'
            : 'Escolha uma stream na lista para assistir.';
    }

    async function refreshStreams() {
        if (!state.session || !state.room) return;
        try {
            setStreams(await api('/streams'));
            syncSubscriptions();
            const hostsNow = state.streams.some((s) => s.hostId === state.identity);
            if (hostsNow && !state.canPublish) {
                // Abriu uma stream depois de entrar: pega um token que pode transmitir
                toast('Você abriu uma stream: liberando o compartilhamento de tela…');
                await connect(await api('/resume', { method: 'POST' }));
            }
        } catch (error) {
            if (error.status === 403) {
                forgetSession();
                if (state.room) await state.room.disconnect();
                showEnded('Sessão encerrada', error.message, false);
            }
        }
    }

    let refreshSoon;
    function scheduleRefresh() {
        clearTimeout(refreshSoon);
        refreshSoon = setTimeout(refreshStreams, 600);
    }

    function startRefresh() {
        stopRefresh();
        state.refreshTimer = setInterval(refreshStreams, 15000);
    }

    function stopRefresh() {
        clearInterval(state.refreshTimer);
        state.refreshTimer = null;
    }

    // ---------- vídeos ----------
    function onTrackSubscribed(track, publication, participant) {
        const tile = ensureTile(participant.identity, streamOf(participant), false);
        if (track.kind === Track.Kind.Video) {
            track.attach(tile.video);
        } else if (track.kind === Track.Kind.Audio) {
            tile.audioTrack = track;
            track.attach(tile.audio);
            track.setVolume(Number(tile.volume.value));
            tile.volume.hidden = false;
        }
    }

    function onTrackUnsubscribed(track, publication, participant) {
        track.detach();
        if (track.kind === Track.Kind.Video) removeTile(participant.identity);
    }

    function addLocalTile(track) {
        const tile = ensureTile(state.identity, state.streams.find((s) => s.hostId === state.identity), true);
        track.attach(tile.video);
    }

    function ensureTile(identity, stream, local) {
        let tile = state.tiles.get(identity);
        if (tile) return tile;
        const root = el('div', 'tile' + (local ? ' local' : ''));
        const video = document.createElement('video');
        video.autoplay = true;
        video.playsInline = true;
        video.muted = true; // o som vem pelo elemento de áudio (e quem transmite não ouve a si mesmo)
        const audio = document.createElement('audio');
        audio.autoplay = true;
        const badge = el('span', 'tile-badge', local ? 'Você está transmitindo' : 'Ao vivo');
        const bar = el('div', 'tile-bar');
        const label = el('div', 'tile-label');
        label.append(el('div', 'tile-title', stream ? stream.title : 'Tela'));
        label.append(el('div', 'tile-host', stream ? stream.hostName : ''));
        const volume = document.createElement('input');
        volume.type = 'range';
        volume.min = '0';
        volume.max = '1';
        volume.step = '0.05';
        volume.value = '1';
        volume.className = 'volume';
        volume.title = 'Volume';
        volume.hidden = true;
        const full = iconButton('⛶', 'Tela cheia', () => {
            if (document.fullscreenElement) document.exitFullscreen();
            else root.requestFullscreen().catch(() => {});
        });
        bar.append(label, volume, full);
        if (!local && stream) {
            bar.append(iconButton('✕', 'Parar de assistir', () => toggleWatch(stream.id)));
        }
        root.append(video, audio, badge, bar);
        tile = { root, video, audio, volume, audioTrack: null };
        volume.addEventListener('input', () => { if (tile.audioTrack) tile.audioTrack.setVolume(Number(volume.value)); });
        state.tiles.set(identity, tile);
        // A sua tela primeiro, depois as outras na ordem em que chegaram
        if (local) $('grid').prepend(root);
        else $('grid').append(root);
        updateGrid();
        return tile;
    }

    function removeTile(identity) {
        const tile = state.tiles.get(identity);
        if (!tile) return;
        tile.root.remove();
        state.tiles.delete(identity);
        updateGrid();
    }

    function clearTiles() {
        [...state.tiles.keys()].forEach(removeTile);
    }

    function updateGrid() {
        $('grid').dataset.count = String(Math.min(state.tiles.size, 6));
    }

    // ---------- transmitir ----------
    async function toggleShare() {
        if (!state.room || !state.canPublish) return;
        const participant = state.room.localParticipant;
        const button = $('share-toggle');
        button.disabled = true;
        try {
            await participant.setScreenShareEnabled(!participant.isScreenShareEnabled, CAPTURE, PUBLISH);
        } catch (error) {
            if (error && error.name !== 'NotAllowedError') toast('Não deu para compartilhar a tela: ' + error.message);
        } finally {
            button.disabled = false;
            updateShareButton();
        }
    }

    function updateShareButton() {
        const button = $('share-toggle');
        const sharing = !!(state.room && state.room.localParticipant.isScreenShareEnabled);
        button.textContent = sharing ? 'Parar de compartilhar' : 'Compartilhar tela';
        button.className = 'btn ' + (sharing ? 'btn-danger' : 'btn-accent');
    }

    function updateAudioButton() {
        $('audio-unlock').hidden = !state.room || state.room.canPlaybackAudio;
    }

    // ---------- chat ----------
    function onData(payload, participant) {
        if (participant) return; // só o bot (servidor) manda mensagens de chat
        const message = parseJson(new TextDecoder().decode(payload));
        if (message && message.type === 'chat') addMessage(message);
    }

    function addMessage(message) {
        if (state.seenMessages.has(message.id)) return;
        state.seenMessages.add(message.id);
        const list = $('messages');
        const nearBottom = list.scrollHeight - list.scrollTop - list.clientHeight < 60;
        const item = el('li', 'msg');
        const avatar = el('div', 'avatar');
        if (message.avatarUrl && /^https:\/\/cdn\.discordapp\.com\//.test(message.avatarUrl)) {
            const img = document.createElement('img');
            img.src = message.avatarUrl;
            img.alt = '';
            img.loading = 'lazy';
            avatar.append(img);
        } else {
            avatar.textContent = (message.author || '?').trim().charAt(0).toUpperCase();
        }
        const body = el('div', 'msg-body');
        const head = el('div', 'msg-head');
        head.append(el('span', 'msg-author', message.author || 'Alguém'));
        head.append(el('span', 'msg-time', new Date(message.timestamp).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })));
        if (message.fromWeb) head.append(el('span', 'msg-web', 'pela stream'));
        body.append(head, el('div', 'msg-text', message.content));
        item.append(avatar, body);
        list.append(item);
        while (list.children.length > 300) list.firstElementChild.remove();
        if (nearBottom || message.fromWeb) list.scrollTop = list.scrollHeight;
    }

    async function sendChat(event) {
        event.preventDefault();
        const input = $('chat-input');
        const text = input.value.trim();
        const error = $('chat-error');
        if (!text) return;
        input.disabled = true;
        try {
            await api('/chat', { method: 'POST', body: { text } });
            input.value = '';
            error.hidden = true;
        } catch (failure) {
            error.textContent = failure.message;
            error.hidden = false;
        } finally {
            input.disabled = false;
            input.focus();
        }
    }

    // ---------- utilitários ----------
    function el(tag, className, text) {
        const node = document.createElement(tag);
        if (className) node.className = className;
        if (text !== undefined) node.textContent = text;
        return node;
    }

    function iconButton(symbol, title, onClick) {
        const button = el('button', 'icon-btn', symbol);
        button.type = 'button';
        button.title = title;
        button.setAttribute('aria-label', title);
        button.addEventListener('click', onClick);
        return button;
    }

    function parseJson(text) {
        try { return text ? JSON.parse(text) : null; } catch { return null; }
    }

    // ---------- início ----------
    const form = $('join-form');
    if (form) form.addEventListener('submit', joinWithCode);
    const codeInput = $('code');
    if (codeInput) {
        codeInput.addEventListener('input', () => {
            // ABCDEFGH → ABCD-EFGH enquanto digita
            const raw = codeInput.value.toUpperCase().replace(/[^A-Z0-9]/g, '').slice(0, 8);
            codeInput.value = raw.length > 4 ? raw.slice(0, 4) + '-' + raw.slice(4) : raw;
        });
    }
    $('share-toggle').addEventListener('click', toggleShare);
    $('audio-unlock').addEventListener('click', () => state.room && state.room.startAudio().then(updateAudioButton));
    $('chat-form').addEventListener('submit', sendChat);
    $('rejoin').addEventListener('click', () => (state.session ? resume() : showJoin()));
    window.addEventListener('beforeunload', () => { if (state.room) state.room.disconnect(); });

    state.session = store.get(SESSION_KEY);
    if (state.session) resume();
    else showJoin();
})();
