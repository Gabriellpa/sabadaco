/*
 * Página da stream. Fluxo:
 *  1. troca o código (uso único) por um token do LiveKit e uma sessão da página (POST /stream/api/{id}/join);
 *  2. conecta na sala do canal de voz sem assinar nada (autoSubscribe: false) e assina só as telas escolhidas:
 *     a da stream do link e as que a pessoa adicionar com "+" (multistream sem pedir outro código);
 *  3. quem abriu uma stream compartilha a tela num dos perfis (1080p60, 1080p30 nítido, 720p30) com simulcast;
 *     quem assiste pode limitar a qualidade de cada tela e vê resolução, fps e a conexão de quem transmite;
 *  4. o chat chega como dado da sala (enviado pelo bot) e sai por POST /stream/api/chat, que posta no Discord.
 * A sessão fica no sessionStorage da aba: com F5 a página pede outro token (POST /stream/api/resume) sem código.
 *
 * A mesma página roda como Atividade do Discord (data-mode="activity"): em vez do código, entra com o login do
 * Discord pelo SDK (POST /stream/api/activity/login) no canal de voz onde a Atividade foi aberta, e o LiveKit passa
 * pelo proxy do Discord (mapeamento /livekit).
 */
(() => {
    'use strict';

    const LK = window.LivekitClient;
    if (!LK) {
        document.getElementById('app').textContent = 'O cliente de vídeo não carregou. Recarregue a página.';
        return;
    }
    const { Room, RoomEvent, Track, VideoPreset, VideoQuality, ConnectionQuality, DisconnectReason } = LK;
    const SCREEN = Track.Source.ScreenShare;
    const SCREEN_AUDIO = Track.Source.ScreenShareAudio;

    const app = document.getElementById('app');
    const streamId = app.dataset.streamId;
    const ACTIVITY = app.dataset.mode === 'activity';
    /** Como pedir um novo acesso, no texto das telas de erro. */
    const AGAIN = ACTIVITY ? 'abra a Atividade de novo' : 'peça outro código com /stream join';
    const $ = (id) => document.getElementById(id);

    const SESSION_KEY = 'sabadaco-stream-session:' + streamId;
    const WATCHING_KEY = 'sabadaco-stream-watching:' + streamId;
    const PREFS_KEY = 'sabadaco-stream-prefs';

    /**
     * Perfis de transmissão. Todos com simulcast: o LiveKit manda para cada pessoa a camada que a rede dela aguenta.
     *  - jogo: 1080p60, segura o fps quando a rede aperta;
     *  - nitidez: 1080p30, segura a resolução (texto, código, planilha);
     *  - leve: 720p30, para internet de upload fraca.
     */
    const PROFILES = {
        game: {
            label: 'Jogo · 1080p60',
            resolution: { width: 1920, height: 1080, frameRate: 60 },
            contentHint: 'motion',
            encoding: { maxBitrate: 6_000_000, maxFramerate: 60 },
            layers: [new VideoPreset(1280, 720, 2_500_000, 30), new VideoPreset(854, 480, 900_000, 15)],
            degradation: 'maintain-framerate',
        },
        detail: {
            label: 'Nitidez · 1080p30',
            resolution: { width: 1920, height: 1080, frameRate: 30 },
            contentHint: 'detail',
            encoding: { maxBitrate: 4_000_000, maxFramerate: 30 },
            layers: [new VideoPreset(1280, 720, 1_800_000, 15), new VideoPreset(854, 480, 600_000, 10)],
            degradation: 'maintain-resolution',
        },
        light: {
            label: 'Leve · 720p30',
            resolution: { width: 1280, height: 720, frameRate: 30 },
            contentHint: 'motion',
            encoding: { maxBitrate: 2_000_000, maxFramerate: 30 },
            layers: [new VideoPreset(640, 360, 600_000, 15)],
            degradation: 'balanced',
        },
    };

    function captureOptions(profile, withAudio) {
        return {
            audio: withAudio,
            systemAudio: withAudio ? 'include' : 'exclude',
            resolution: profile.resolution,
            contentHint: profile.contentHint,
            surfaceSwitching: 'include',
            selfBrowserSurface: 'exclude',
        };
    }

    function publishOptions(profile) {
        return {
            videoCodec: 'vp8',
            simulcast: true,
            screenShareEncoding: profile.encoding,
            screenShareSimulcastLayers: profile.layers,
            degradationPreference: profile.degradation,
        };
    }

    /** Limite de qualidade que quem assiste escolhe por tela (Auto = o LiveKit decide pelo tamanho da tela e pela rede). */
    const QUALITIES = [
        { id: 'auto', label: 'Auto', value: VideoQuality.HIGH },
        { id: 'high', label: 'Alta', value: VideoQuality.HIGH },
        { id: 'medium', label: 'Média', value: VideoQuality.MEDIUM },
        { id: 'low', label: 'Baixa', value: VideoQuality.LOW },
    ];

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
        statsTimer: null,
        quality: new Map(), // identidade → qualidade da conexão de quem transmite
        autoWatch: false, // Atividade sem stream no canal: assiste a primeira que abrir
        channelTitle: false, // Atividade entrou sem stream: o título é o do canal
    };

    // ---------- armazenamento (pode falhar em aba anônima: tudo continua funcionando sem ele) ----------
    const store = {
        get(key) { try { return sessionStorage.getItem(key); } catch { return null; } },
        set(key, value) { try { sessionStorage.setItem(key, value); } catch { /* sem armazenamento */ } },
        remove(key) { try { sessionStorage.removeItem(key); } catch { /* sem armazenamento */ } },
    };
    /** Preferências que valem para todas as streams (perfil e áudio de quem transmite). */
    const prefs = Object.assign({ profile: 'game', audio: true }, parseJson(readLocal(PREFS_KEY)) || {});
    if (!PROFILES[prefs.profile]) prefs.profile = 'game';

    function readLocal(key) { try { return localStorage.getItem(key); } catch { return null; } }
    function savePrefs() { try { localStorage.setItem(PREFS_KEY, JSON.stringify(prefs)); } catch { /* sem armazenamento */ } }

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
        if (input) {
            input.focus();
            if (error) {
                const card = $('join-form');
                card.classList.remove('shake');
                void card.offsetWidth; // reinicia a animação
                card.classList.add('shake');
            }
        }
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
        button.innerHTML = '<span class="spinner"></span>Entrando…';
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
                if (ACTIVITY) joinWithDiscord();
                else showJoin(error.message);
            } else {
                showEnded('Não deu para reconectar', error.message, true);
            }
        }
    }

    // ---------- entrar pela Atividade do Discord ----------
    let discordSdk = null;

    async function joinWithDiscord() {
        show('join');
        setStatus('connecting', 'Entrando…');
        $('join-error').hidden = true;
        $('activity-retry').hidden = true;
        $('activity-text').textContent = 'Entrando com a sua conta do Discord…';
        try {
            const sdk = window.DiscordEmbeddedSdk;
            const clientId = app.dataset.clientId;
            if (!sdk || !clientId) throw new Error('A Atividade ainda não foi configurada neste bot.');
            if (!discordSdk) {
                discordSdk = new sdk.DiscordSDK(clientId);
                await discordSdk.ready();
            }
            const { code } = await discordSdk.commands.authorize({
                client_id: clientId, response_type: 'code', state: '', prompt: 'none', scope: ['identify'],
            });
            // O bot troca o código por quem a pessoa é e devolve o mesmo acesso que o código da DM daria
            const ticket = await api('/activity/login', {
                method: 'POST', auth: false,
                body: { code, guildId: discordSdk.guildId, channelId: discordSdk.channelId },
            });
            await connect(ticket);
        } catch (error) {
            show('join');
            setStatus('error', 'Desconectado');
            $('activity-text').textContent = 'Não deu para entrar.';
            $('join-error').textContent = (error && error.message) || 'Algo deu errado.';
            $('join-error').hidden = false;
            $('activity-retry').hidden = false;
        }
    }

    /**
     * Dentro do Discord só passa o que tem mapeamento no proxy (Developer Portal → URL Mappings: /livekit →
     * <projeto>.livekit.cloud). Por isso a Atividade conecta em wss://<app>.discordsays.com/livekit em vez do
     * endereço do LiveKit. Com o endereço do proxy o cliente também não tenta os servidores regionais do LiveKit
     * Cloud, que o Discord bloquearia.
     */
    function livekitUrl(serverUrl) {
        if (!ACTIVITY) return serverUrl;
        const scheme = location.protocol === 'https:' ? 'wss:' : 'ws:';
        return `${scheme}//${location.host}/livekit`;
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
            const initial = Array.isArray(saved) && saved.length ? saved : (ticket.stream ? [ticket.stream.id] : []);
            initial.forEach((id) => state.watching.add(id));
            // Atividade aberta num canal sem stream: a primeira que aparecer já entra na tela
            state.autoWatch = state.watching.size === 0;
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
        await room.connect(livekitUrl(ticket.serverUrl), ticket.token, { autoSubscribe: false });

        show('room');
        setStatus('connected', 'Conectado');
        const title = $('room-title');
        state.channelTitle = !ticket.stream;
        if (ticket.stream) {
            title.replaceChildren(el('strong', '', ticket.stream.title), document.createTextNode(' · ' + ticket.stream.hostName));
        }
        // Na Atividade o botão abre o navegador (transmitir ou abrir uma stream), então aparece para todos
        $('share-group').hidden = !(state.canPublish || ACTIVITY);
        updateActivityHeader();
        updateShareButton();
        updateAudioButton();
        updateViewers();
        syncSubscriptions();
        startRefresh();
        startStats();
    }

    function wire(room) {
        const resync = () => { updateViewers(); syncSubscriptions(); scheduleRefresh(); };
        const people = () => renderPeople();
        room
            .on(RoomEvent.ParticipantConnected, resync)
            .on(RoomEvent.ParticipantDisconnected, (participant) => { removeTile(participant.identity); resync(); })
            .on(RoomEvent.ParticipantMetadataChanged, resync)
            .on(RoomEvent.TrackPublished, resync)
            .on(RoomEvent.TrackUnpublished, (publication, participant) => {
                if (publication.source === SCREEN) removeTile(participant.identity);
                renderStreams();
            })
            .on(RoomEvent.ParticipantNameChanged, people)
            .on(RoomEvent.ConnectionQualityChanged, (quality, participant) => {
                state.quality.set(participant.identity, quality);
                const tile = state.tiles.get(participant.identity);
                if (tile) tile.root.dataset.connection = quality;
                renderPeople();
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
        stopStats();
        clearTiles();
        if (reason === DisconnectReason.PARTICIPANT_REMOVED) {
            forgetSession();
            showEnded('Você saiu do canal de voz',
                ACTIVITY ? 'A stream fecha quando você sai do canal de voz dela. Entre de novo no canal e abra a Atividade.'
                    : 'A stream fecha quando você sai do canal de voz dela. Entre de novo no canal e use /stream join para receber outro código.',
                ACTIVITY);
        } else if (reason === DisconnectReason.DUPLICATE_IDENTITY) {
            showEnded('Aberta em outra aba', 'Você entrou nesta stream em outra aba ou aparelho. Esta aqui foi fechada.', true);
        } else if (reason === DisconnectReason.CLIENT_INITIATED) {
            showEnded('Você saiu da stream', '', true);
        } else {
            showEnded('A conexão caiu', 'Tente reconectar. Se não der, ' + AGAIN + '.', true);
        }
    }

    // ---------- streams do canal e assinaturas ----------
    function setStreams(list) {
        state.streams = list || [];
        // Quem não está mais ao vivo sai da seleção
        const live = new Set(state.streams.map((s) => s.id));
        [...state.watching].forEach((id) => { if (!live.has(id)) state.watching.delete(id); });
        if (state.autoWatch) {
            const first = state.streams.find((s) => s.hostId !== state.identity);
            if (first) {
                state.watching.add(first.id);
                state.autoWatch = false;
            }
        }
        renderStreams();
        updateActivityHeader();
    }

    /** Atividade: "Abrir no navegador" só com stream no canal; sem stream de entrada, o título conta as do canal. */
    function updateActivityHeader() {
        if (!ACTIVITY || !state.room) return;
        $('open-browser').hidden = state.streams.length === 0;
        if (state.channelTitle) {
            const live = state.streams.length;
            $('room-title').replaceChildren(el('strong', '', 'Canal de voz'), document.createTextNode(' · '
                + (live === 0 ? 'ninguém transmitindo' : live === 1 ? '1 stream ao vivo' : live + ' streams ao vivo')));
        }
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
        state.streams.forEach((stream, index) => {
            const own = stream.hostId === state.identity;
            const watching = own || state.watching.has(stream.id);
            const sharing = isSharing(stream);
            const item = el('li', 'stream-item' + (watching && !own ? ' watching' : ''));
            item.style.setProperty('--hue', String(hueOf(stream.id)));
            item.style.animationDelay = (index * 40) + 'ms';
            item.append(el('div', 'stream-dot', initial(stream.title)));
            const info = el('div', 'stream-info');
            info.append(el('div', 'stream-title', stream.title));
            info.append(el('div', 'stream-host', own ? 'Sua stream' : stream.hostName));
            info.append(el('div', 'stream-state' + (sharing ? ' on' : ''), sharing ? 'transmitindo' : 'esperando a tela'));
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
        $('stream-count').textContent = String(state.streams.length);
        updateEmptyText();
        renderPeople();
    }

    /** Quem está na sala: você primeiro, depois quem transmite, depois o resto por nome. */
    function renderPeople() {
        const list = $('people');
        if (!state.room) return;
        const local = state.room.localParticipant;
        const everyone = [{ participant: local, me: true }]
            .concat([...state.room.remoteParticipants.values()].map((participant) => ({ participant, me: false })));
        const sharingOf = (p, me) => (me ? local.isScreenShareEnabled : !!p.getTrackPublication(SCREEN));
        everyone.sort((a, b) => (b.me - a.me) || (sharingOf(b.participant, b.me) - sharingOf(a.participant, a.me))
            || nameOf(a.participant).localeCompare(nameOf(b.participant), 'pt-BR'));
        list.replaceChildren(...everyone.map(({ participant, me }) => {
            const name = nameOf(participant);
            const item = el('li', 'person' + (sharingOf(participant, me) ? ' live' : ''));
            const avatar = el('span', 'avatar avatar-sm', initial(name));
            avatar.style.setProperty('--hue', String(hueOf(name)));
            item.append(avatar, el('span', 'person-name', me ? name + ' (você)' : name));
            if (sharingOf(participant, me)) item.append(el('span', 'person-live', 'ao vivo'));
            const quality = me ? null : state.quality.get(participant.identity);
            if (quality && quality !== ConnectionQuality.Unknown) {
                const dot = el('span', 'net net-' + quality);
                dot.title = 'Conexão: ' + ({ excellent: 'ótima', good: 'boa', poor: 'ruim', lost: 'caiu' }[quality] || quality);
                item.append(dot);
            }
            return item;
        }));
        $('people-count').textContent = String(everyone.length);
    }

    function nameOf(participant) {
        return participant.name || participant.identity || 'Alguém';
    }

    function updateEmptyText() {
        const anyWatching = state.streams.some((s) => s.hostId !== state.identity && state.watching.has(s.id));
        $('stage-empty-text').textContent = anyWatching
            ? 'Esperando quem transmite compartilhar a tela…'
            : state.streams.length === 0 && ACTIVITY
                ? 'Ninguém transmitindo neste canal. Clique em Transmitir para começar.'
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
        tile.participant = participant;
        if (track.kind === Track.Kind.Video) {
            tile.videoTrack = track;
            tile.videoPublication = publication;
            track.attach(tile.video);
            applyQuality(tile);
        } else if (track.kind === Track.Kind.Audio) {
            tile.audioTrack = track;
            track.attach(tile.audio);
            applyVolume(tile);
            tile.volume.hidden = false;
            tile.mute.hidden = false;
        }
        const quality = state.quality.get(participant.identity);
        if (quality) tile.root.dataset.connection = quality;
    }

    function onTrackUnsubscribed(track, publication, participant) {
        track.detach();
        if (track.kind === Track.Kind.Video) removeTile(participant.identity);
    }

    function addLocalTile(track) {
        const tile = ensureTile(state.identity, state.streams.find((s) => s.hostId === state.identity), true);
        tile.videoTrack = track;
        track.attach(tile.video);
    }

    function ensureTile(identity, stream, local) {
        let tile = state.tiles.get(identity);
        if (tile) return tile;
        const root = el('div', 'tile' + (local ? ' local' : ''));
        root.style.setProperty('--hue', String(stream ? hueOf(stream.id) : 24));
        root.style.viewTransitionName = 'tile-' + identity.replace(/[^a-zA-Z0-9_-]/g, '');
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
        const stats = el('span', 'tile-stats', local ? 'enviando…' : 'conectando…');
        const volume = document.createElement('input');
        volume.type = 'range';
        volume.min = '0';
        volume.max = '1';
        volume.step = '0.05';
        volume.value = '1';
        volume.className = 'volume';
        volume.title = 'Volume';
        volume.hidden = true;
        const full = iconButton('⛶', 'Tela cheia (F)', () => toggleFullscreen(root));
        const mute = iconButton('🔊', 'Silenciar (M)', () => { tile.muted = !tile.muted; applyVolume(tile); });
        mute.hidden = true;
        const quality = el('button', 'quality-btn', 'Auto');
        quality.type = 'button';
        quality.title = 'Qualidade máxima desta tela';
        quality.addEventListener('click', () => {
            const index = QUALITIES.findIndex((q) => q.id === tile.quality);
            tile.quality = QUALITIES[(index + 1) % QUALITIES.length].id;
            applyQuality(tile);
        });
        const controls = [];
        if (!local) controls.push(mute, volume, quality);
        if (document.pictureInPictureEnabled) {
            controls.push(iconButton('⧉', 'Janela flutuante', () => {
                if (document.pictureInPictureElement === video) document.exitPictureInPicture().catch(() => {});
                else video.requestPictureInPicture().catch(() => {});
            }));
        }
        controls.push(full);
        bar.append(label, ...controls);
        if (!local && stream) {
            bar.append(iconButton('✕', 'Parar de assistir', () => toggleWatch(stream.id)));
        }
        video.addEventListener('dblclick', () => toggleFullscreen(root));
        root.append(video, audio, badge, stats, bar);
        tile = {
            root, video, audio, volume, mute, stats, local,
            qualityButton: quality, quality: 'auto', muted: false,
            audioTrack: null, videoTrack: null, videoPublication: null, participant: null, last: null,
        };
        volume.addEventListener('input', () => { tile.muted = false; applyVolume(tile); });
        state.tiles.set(identity, tile);
        // A sua tela primeiro, depois as outras na ordem em que chegaram
        transition(() => {
            if (local) $('grid').prepend(root);
            else $('grid').append(root);
            updateGrid();
        });
        return tile;
    }

    function removeTile(identity) {
        const tile = state.tiles.get(identity);
        if (!tile) return;
        state.tiles.delete(identity);
        if (document.startViewTransition && !reducedMotion()) {
            transition(() => { tile.root.remove(); updateGrid(); });
        } else {
            tile.root.classList.add('leaving');
            setTimeout(() => { tile.root.remove(); updateGrid(); }, 280);
        }
    }

    function applyVolume(tile) {
        const level = tile.muted ? 0 : Number(tile.volume.value);
        if (tile.audioTrack) tile.audioTrack.setVolume(level);
        tile.mute.textContent = level === 0 ? '🔇' : '🔊';
        tile.mute.title = level === 0 ? 'Ativar som (M)' : 'Silenciar (M)';
    }

    function applyQuality(tile) {
        const choice = QUALITIES.find((q) => q.id === tile.quality) || QUALITIES[0];
        tile.qualityButton.textContent = choice.label;
        tile.qualityButton.classList.toggle('forced', choice.id !== 'auto');
        if (tile.videoPublication) tile.videoPublication.setVideoQuality(choice.value);
    }

    function toggleFullscreen(root) {
        if (document.fullscreenElement) document.exitFullscreen().catch(() => {});
        else root.requestFullscreen().catch(() => {});
    }

    // ---------- estatísticas: resolução e fps que chegam (ou saem, para quem transmite) ----------
    function startStats() {
        stopStats();
        state.statsTimer = setInterval(updateStats, 2000);
    }

    function stopStats() {
        clearInterval(state.statsTimer);
        state.statsTimer = null;
    }

    async function updateStats() {
        for (const tile of state.tiles.values()) {
            if (!tile.videoTrack) continue;
            if (tile.local) {
                const settings = tile.videoTrack.mediaStreamTrack ? tile.videoTrack.mediaStreamTrack.getSettings() : {};
                const audio = state.room && state.room.localParticipant.getTrackPublication(SCREEN_AUDIO);
                tile.stats.textContent = 'enviando ' + describe(settings.height, settings.frameRate) + (audio ? '' : ' · sem áudio');
                continue;
            }
            let stats;
            try { stats = await tile.videoTrack.getReceiverStats(); } catch { stats = null; }
            if (!stats) continue;
            const previous = tile.last;
            tile.last = stats;
            if (!previous) continue;
            const seconds = (stats.timestamp - previous.timestamp) / 1000;
            if (seconds <= 0) continue;
            const fps = (stats.framesDecoded - previous.framesDecoded) / seconds;
            const mbps = ((stats.bytesReceived || 0) - (previous.bytesReceived || 0)) * 8 / seconds / 1e6;
            const hasAudio = tile.participant && tile.participant.getTrackPublication(SCREEN_AUDIO);
            tile.stats.textContent = describe(stats.frameHeight, fps) + ' · ' + mbps.toFixed(1) + ' Mbps' + (hasAudio ? '' : ' · sem áudio');
        }
    }

    function describe(height, fps) {
        const parts = [];
        if (height) parts.push(Math.round(height) + 'p');
        if (fps) parts.push(Math.round(fps) + ' fps');
        return parts.join(' · ') || '…';
    }

    function clearTiles() {
        [...state.tiles.keys()].forEach(removeTile);
    }

    function updateGrid() {
        $('grid').dataset.count = String(Math.min(state.tiles.size, 6));
    }

    // ---------- transmitir ----------
    async function toggleShare() {
        if (ACTIVITY) {
            broadcastFromActivity();
            return;
        }
        if (!state.room || !state.canPublish) return;
        const participant = state.room.localParticipant;
        const button = $('share-toggle');
        button.disabled = true;
        const enabling = !participant.isScreenShareEnabled;
        const profile = PROFILES[prefs.profile];
        try {
            await participant.setScreenShareEnabled(enabling, captureOptions(profile, prefs.audio), publishOptions(profile));
            if (enabling && participant.isScreenShareEnabled && prefs.audio && !participant.getTrackPublication(SCREEN_AUDIO)) {
                toast('Transmitindo sem áudio. No Chrome/Edge, marque "Compartilhar áudio" ao escolher uma aba ou a tela inteira (janela sozinha não tem áudio).');
            }
        } catch (error) {
            if (error && error.name !== 'NotAllowedError') {
                toast('Não deu para compartilhar a tela: ' + error.message);
            }
        } finally {
            button.disabled = false;
            updateShareButton();
        }
    }

    // ---------- Atividade → navegador ----------
    // O Discord bloqueia getDisplayMedia no iframe da Atividade (permissions policy "display-capture"). Quem quer
    // transmitir (ou prefere assistir no navegador) recebe um link com código de uso único e o SDK abre o navegador.

    /** @returns false se a pessoa recusou abrir o link no aviso do Discord */
    async function openInBrowser(path, body) {
        const { url } = await api(path, { method: 'POST', body });
        try {
            const result = await discordSdk.commands.openExternalLink({ url });
            return !result || result.opened !== false;
        } catch (error) {
            throw new Error('O Discord não abriu o navegador' + (error && error.message ? ': ' + error.message : '.'));
        }
    }

    async function watchInBrowser() {
        const button = $('open-browser');
        button.disabled = true;
        try {
            const watching = state.streams.find((s) => state.watching.has(s.id));
            await openInBrowser('/activity/browser', { streamId: watching ? watching.id : null });
        } catch (error) {
            toast(error.message);
        } finally {
            button.disabled = false;
        }
    }

    async function broadcastFromActivity() {
        if (state.streams.some((s) => s.hostId === state.identity)) {
            // Já tem stream: só abre o navegador nela, pronto para compartilhar
            const button = $('share-toggle');
            button.disabled = true;
            try {
                if (await openInBrowser('/activity/browser', {})) toast('No navegador, clique em "Compartilhar tela".');
            } catch (error) {
                toast(error.message);
            } finally {
                button.disabled = false;
            }
            return;
        }
        $('broadcast-error').hidden = true;
        $('broadcast-dialog').hidden = false;
        $('broadcast-title').focus();
    }

    async function submitBroadcast(event) {
        event.preventDefault();
        const button = $('broadcast-submit');
        const title = $('broadcast-title').value.trim();
        if (!title) return;
        button.disabled = true;
        button.innerHTML = '<span class="spinner"></span>Abrindo…';
        try {
            const opened = await openInBrowser('/activity/stream', { title });
            $('broadcast-dialog').hidden = true;
            $('broadcast-title').value = '';
            toast(opened
                ? 'Stream aberta. No navegador, clique em "Compartilhar tela".'
                : 'Stream aberta. Clique em Transmitir de novo para abrir o navegador.');
            scheduleRefresh();
        } catch (error) {
            $('broadcast-error').textContent = error.message;
            $('broadcast-error').hidden = false;
        } finally {
            button.disabled = false;
            button.textContent = 'Abrir stream e navegador';
        }
    }

    function updateShareButton() {
        const button = $('share-toggle');
        const sharing = !!(state.room && state.room.localParticipant.isScreenShareEnabled);
        if (ACTIVITY) {
            button.textContent = '📡 Transmitir';
            button.title = 'Abre o seu navegador pronto para compartilhar a tela (o Discord não deixa pela Atividade)';
            return;
        }
        button.textContent = sharing ? 'Parar de compartilhar' : 'Compartilhar tela';
        button.className = 'btn ' + (sharing ? 'btn-danger' : 'btn-accent');
        // Perfil e áudio valem a partir da próxima vez que compartilhar
        $('share-profile').disabled = sharing;
        $('share-audio').disabled = sharing;
        $('share-settings').classList.toggle('locked', sharing);
    }

    function updateViewers() {
        const chip = $('viewers');
        chip.hidden = !state.room;
        if (state.room) $('viewers-count').textContent = String(state.room.remoteParticipants.size + 1);
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
            avatar.textContent = initial(message.author);
            avatar.style.setProperty('--hue', String(hueOf(message.author || '')));
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

    /** Cor estável por texto (id da stream, nome): a mesma stream tem a mesma cor na lista e na tela. */
    function hueOf(text) {
        let hash = 0;
        for (const char of String(text)) hash = (hash * 31 + char.codePointAt(0)) >>> 0;
        return Math.round((hash % 997) * 137.508) % 360; // ângulo de ouro: ids parecidos ficam com cores bem diferentes
    }

    function initial(text) {
        return ((text || '?').trim().charAt(0) || '?').toUpperCase();
    }

    function reducedMotion() {
        return window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    }

    /** Anima a troca do DOM com View Transitions quando o navegador suporta. */
    function transition(change) {
        if (document.startViewTransition && !reducedMotion() && document.visibilityState === 'visible') {
            document.startViewTransition(change);
        } else {
            change();
        }
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
            codeInput.classList.toggle('complete', raw.length === 8);
        });
    }
    $('share-toggle').addEventListener('click', toggleShare);
    const profileSelect = $('share-profile');
    Object.entries(PROFILES).forEach(([id, profile]) => {
        const option = el('option', '', profile.label);
        option.value = id;
        profileSelect.append(option);
    });
    profileSelect.value = prefs.profile;
    profileSelect.addEventListener('change', () => { prefs.profile = profileSelect.value; savePrefs(); });
    const audioToggle = $('share-audio');
    audioToggle.checked = prefs.audio;
    audioToggle.addEventListener('change', () => { prefs.audio = audioToggle.checked; savePrefs(); });

    // Atalhos: F = tela cheia da tela sob o mouse (ou a primeira), M = silenciar/ativar todas
    document.addEventListener('keydown', (event) => {
        if (event.ctrlKey || event.metaKey || event.altKey || /^(INPUT|TEXTAREA|SELECT)$/.test(event.target.tagName)) return;
        const tiles = [...state.tiles.values()];
        if (!tiles.length) return;
        if (event.key === 'f' || event.key === 'F') {
            const hovered = tiles.find((tile) => tile.root.matches(':hover')) || tiles[0];
            toggleFullscreen(hovered.root);
        } else if (event.key === 'm' || event.key === 'M') {
            const remote = tiles.filter((tile) => !tile.local);
            const muteAll = remote.some((tile) => !tile.muted);
            remote.forEach((tile) => { tile.muted = muteAll; applyVolume(tile); });
            if (remote.length) toast(muteAll ? 'Som desligado (M para ligar)' : 'Som ligado');
        }
    });
    $('audio-unlock').addEventListener('click', () => state.room && state.room.startAudio().then(updateAudioButton));
    $('chat-form').addEventListener('submit', sendChat);
    $('rejoin').addEventListener('click', () => (state.session ? resume() : (ACTIVITY ? joinWithDiscord() : showJoin())));
    const activityRetry = $('activity-retry');
    if (activityRetry) activityRetry.addEventListener('click', joinWithDiscord);
    if (ACTIVITY) {
        $('open-browser').addEventListener('click', watchInBrowser);
        $('broadcast-form').addEventListener('submit', submitBroadcast);
        $('broadcast-cancel').addEventListener('click', () => { $('broadcast-dialog').hidden = true; });
    }
    // Holofote: o brilho dos painéis de vidro segue o mouse
    document.addEventListener('pointermove', (event) => {
        const glass = event.target.closest && event.target.closest('.glass');
        if (!glass) return;
        const box = glass.getBoundingClientRect();
        glass.style.setProperty('--mx', (event.clientX - box.left) + 'px');
        glass.style.setProperty('--my', (event.clientY - box.top) + 'px');
    }, { passive: true });
    window.addEventListener('beforeunload', () => { if (state.room) state.room.disconnect(); });

    if (ACTIVITY) {
        // Na Atividade a entrada é sempre pelo login do Discord (rápido e sem digitar nada)
        joinWithDiscord();
    } else {
        // Link aberto pela Atividade: o código vem no fragmento (#code=...), que não vai para o servidor
        const fromLink = new URLSearchParams(location.hash.slice(1)).get('code');
        if (fromLink) {
            history.replaceState(null, '', location.pathname + location.search);
            forgetSession();
            showJoin();
            if (codeInput) {
                codeInput.value = fromLink;
                form.requestSubmit();
            }
        } else {
            state.session = store.get(SESSION_KEY);
            if (state.session) resume();
            else showJoin();
        }
    }
})();
