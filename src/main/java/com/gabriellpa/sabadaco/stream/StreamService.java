package com.gabriellpa.sabadaco.stream;

import com.gabriellpa.sabadaco.DiscordAdmins;
import com.gabriellpa.sabadaco.UserFacingException;
import de.mkammerer.snowflakeid.SnowflakeIdGenerator;
import de.mkammerer.snowflakeid.options.Options;
import de.mkammerer.snowflakeid.structure.Structure;
import de.mkammerer.snowflakeid.time.MonotonicTimeSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Regras do streaming de tela: abrir, encontrar e encerrar streams, emitir códigos e trocar um código
 * por um acesso à sala de mídia. Não conhece Discord nem web (como o núcleo de música).
 *
 * <p>Segurança: o código chega por DM, é da pessoa, vale 5 min e uma vez só. Ao trocar o código pelo
 * acesso, a pessoa ainda precisa estar no mesmo servidor e no mesmo canal de voz da stream.
 */
@Service
public class StreamService {

    static final int MAX_TITLE_LENGTH = 100;
    /** Sessão da Atividade aberta num canal sem stream (não há stream 0: os ids são snowflakes). */
    static final long NO_STREAM = 0;
    /** Sufixo da identidade de quem está na Atividade (ver {@link #identityOf}). */
    static final String ACTIVITY_IDENTITY = "#atividade";
    /** Aviso na sala de que a lista de streams do canal mudou: a página busca a lista na hora, sem esperar o refresh. */
    static final byte[] STREAMS_CHANGED = "{\"type\":\"streams\"}".getBytes(StandardCharsets.UTF_8);

    private final LongSupplier ids;
    private final JoinCodeIssuer codes;
    private final MediaTokenIssuer media;
    private final MediaRooms rooms;
    private final ViewerSessions sessions;
    private final VoicePresence presence;
    private final DiscordAdmins admins;
    private final Clock clock;
    private final String publicUrl;
    private final Map<Long, StreamSession> live = new ConcurrentHashMap<>();

    /** Início da contagem dos IDs snowflake (como o 2015-01-01 do Discord). */
    static final Instant ID_EPOCH = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired
    public StreamService(StreamProperties properties, JoinCodeIssuer codes, MediaTokenIssuer media, MediaRooms rooms,
                         ViewerSessions sessions, VoicePresence presence, DiscordAdmins admins) {
        this(snowflakes(properties.node()), codes, media, rooms, sessions, presence, admins, Clock.systemUTC(),
                properties.publicUrl());
    }

    StreamService(LongSupplier ids, JoinCodeIssuer codes, MediaTokenIssuer media, MediaRooms rooms,
                  ViewerSessions sessions, VoicePresence presence, DiscordAdmins admins, Clock clock, String publicUrl) {
        this.ids = ids;
        this.codes = codes;
        this.media = media;
        this.rooms = rooms;
        this.sessions = sessions;
        this.presence = presence;
        this.admins = admins;
        this.clock = clock;
        this.publicUrl = publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;
    }

    /** Link e código que vão por DM. */
    public record Invite(StreamSession stream, String link, JoinCodeIssuer.JoinCode code) {

        /**
         * O link com o código no fragmento ({@code #code=...}): a página do navegador entra sozinha. O fragmento não
         * vai para o servidor, então o código não aparece em log de proxy nem de ingress.
         */
        public String linkWithCode() {
            return link + "#code=" + code.code();
        }
    }

    /** @param created {@code false} quando a pessoa já tinha uma stream e o link é dela */
    public record Opened(Invite invite, boolean created) {
    }

    /**
     * O que o navegador recebe para conectar na sala de mídia.
     *
     * @param stream  a stream por onde a pessoa entrou; {@code null} na Atividade aberta num canal ainda sem stream
     * @param session token opaco da página (chat e lista de streams); revogado quando a pessoa sai do canal de voz
     */
    public record JoinTicket(String serverUrl, String token, String room, String identity, boolean canPublish,
                             StreamSession stream, List<StreamSession> channelStreams, String session) {
    }

    /**
     * Abre uma stream no canal de voz onde quem pediu está. Quem transmite também recebe um código,
     * porque é pela mesma página que ele compartilha a tela.
     */
    public Invite create(long guildId, long textChannelId, long hostUserId, String hostName, String title) {
        requireConfigured();
        var cleanTitle = title == null ? "" : title.strip();
        if (cleanTitle.isEmpty()) {
            throw new UserFacingException("Dê um título para a stream.");
        }
        if (cleanTitle.length() > MAX_TITLE_LENGTH) {
            throw new UserFacingException("O título pode ter até " + MAX_TITLE_LENGTH + " caracteres.");
        }
        long voiceChannelId = currentVoiceChannel(guildId, hostUserId);
        if (hostedBy(guildId, hostUserId).isPresent()) {
            throw new UserFacingException("Você já tem uma stream aberta. Encerre a atual antes de abrir outra.");
        }
        boolean titleTaken = streamsIn(guildId, voiceChannelId).stream()
                .anyMatch(stream -> stream.title().equalsIgnoreCase(cleanTitle));
        if (titleTaken) {
            throw new UserFacingException("Já existe uma stream chamada \"" + cleanTitle + "\" neste canal.");
        }
        var stream = new StreamSession(ids.getAsLong(), cleanTitle, guildId, voiceChannelId, textChannelId,
                hostUserId, hostName, clock.instant());
        live.put(stream.id(), stream);
        rooms.broadcast(stream.room(), STREAMS_CHANGED);
        return invite(stream, hostUserId, hostName);
    }

    /**
     * Gera o código para assistir a uma stream do canal de voz onde a pessoa está. Ele só vale no link dessa
     * stream; lá dentro ela vê também as outras streams do canal.
     *
     * @param query id da stream (autocomplete), título, nome de quem transmite ou id dele; vazio = a primeira do canal
     */
    public Invite join(long guildId, long userId, String displayName, String query) {
        requireConfigured();
        long voiceChannelId = currentVoiceChannel(guildId, userId);
        var streams = streamsIn(guildId, voiceChannelId);
        if (streams.isEmpty()) {
            throw new UserFacingException("Não há stream ao vivo no seu canal de voz.");
        }
        var stream = find(streams, query)
                .orElseThrow(() -> new UserFacingException("Nenhuma stream do seu canal de voz corresponde a \"" + query.strip() + "\"."));
        return invite(stream, userId, displayName);
    }

    /** Código para uma stream específica (botão "Assistir" do card). A pessoa precisa estar no canal de voz dela. */
    public Invite joinStream(long guildId, long userId, String displayName, long streamId) {
        requireConfigured();
        var stream = Optional.ofNullable(live.get(streamId))
                .filter(s -> s.guildId() == guildId)
                .orElseThrow(() -> new UserFacingException("Esta stream já terminou."));
        var current = presence.voiceChannelOf(guildId, userId);
        if (current.isEmpty() || current.get() != stream.voiceChannelId()) {
            throw new UserFacingException("Entre no canal de voz da stream para assistir.");
        }
        return invite(stream, userId, displayName);
    }

    /** Troca o código (uso único) pelo acesso à sala. Chamado pela página da stream. */
    public JoinTicket redeem(long streamId, String code) {
        requireConfigured();
        var grant = codes.redeem(code)
                .orElseThrow(() -> new UserFacingException("Código inválido ou expirado. Peça outro com /stream join."));
        var stream = Optional.ofNullable(live.get(streamId))
                .orElseThrow(() -> new UserFacingException("Esta stream já terminou."));
        if (grant.streamId() != stream.id()) {
            throw new UserFacingException("Este código é de outra stream: use o link que veio junto com ele.");
        }
        var current = presence.voiceChannelOf(stream.guildId(), grant.userId());
        if (current.isEmpty() || current.get() != stream.voiceChannelId()) {
            throw new UserFacingException("Entre no canal de voz da stream e peça um novo código.");
        }
        var session = sessions.open(stream.id(), stream.guildId(), stream.voiceChannelId(), grant.userId(), grant.displayName());
        return ticket(stream.guildId(), stream.voiceChannelId(), stream, grant.userId(), grant.displayName(), session.token(),
                false);
    }

    /**
     * Entrada pela Atividade do Discord: a pessoa já provou quem é pelo login do Discord (não tem código por DM) e
     * abriu a Atividade num canal de voz. Vale a mesma regra do código: ela precisa estar nesse canal agora. Entra na
     * stream que ela transmite, se tiver, ou na primeira do canal; lá dentro vê todas as outras. Sem stream no canal,
     * entra na sala vazia (dá para abrir uma stream pela própria Atividade).
     */
    public JoinTicket joinFromDiscord(long guildId, long voiceChannelId, long userId, String displayName) {
        requireConfigured();
        var current = presence.voiceChannelOf(guildId, userId);
        if (current.isEmpty() || current.get() != voiceChannelId) {
            throw new UserFacingException("Entre no canal de voz onde a Atividade foi aberta.");
        }
        var channelStreams = streamsIn(guildId, voiceChannelId);
        var stream = channelStreams.stream().filter(s -> s.hostUserId() == userId).findFirst()
                .or(() -> channelStreams.stream().findFirst())
                .orElse(null);
        var session = sessions.open(stream == null ? NO_STREAM : stream.id(), guildId, voiceChannelId, userId, displayName,
                true);
        return ticket(guildId, voiceChannelId, stream, userId, displayName, session.token(), true);
    }

    /**
     * Link do navegador, já com código, para quem está na Atividade: assistir pelo navegador ou transmitir (só o
     * navegador consegue compartilhar a tela; o Discord bloqueia na Atividade).
     *
     * @param streamId a stream que a pessoa quer abrir; {@code null} = a dela, se tiver, ou a primeira do canal
     */
    public Invite browserInvite(String sessionToken, Long streamId) {
        requireConfigured();
        var session = sessionInItsChannel(sessionToken);
        var channelStreams = streamsIn(session.guildId(), session.voiceChannelId());
        var stream = (streamId != null
                ? channelStreams.stream().filter(s -> s.id() == streamId).findFirst()
                : channelStreams.stream().filter(s -> s.hostUserId() == session.userId()).findFirst()
                        .or(() -> channelStreams.stream().findFirst()))
                .orElseThrow(() -> new UserFacingException(streamId != null
                        ? "Esta stream já terminou." : "Não há stream ao vivo neste canal."));
        return invite(stream, session.userId(), session.displayName());
    }

    /**
     * Abre uma stream pela Atividade, no canal de voz dela (o chat da stream fica no chat do próprio canal de voz).
     * Se a pessoa já transmite, devolve o link da stream dela em vez de abrir outra.
     */
    public Opened openFromActivity(String sessionToken, String title) {
        requireConfigured();
        var session = sessionInItsChannel(sessionToken);
        var hosted = hostedBy(session.guildId(), session.userId());
        if (hosted.isPresent()) {
            return new Opened(invite(hosted.get(), session.userId(), session.displayName()), false);
        }
        return new Opened(create(session.guildId(), session.voiceChannelId(), session.userId(), session.displayName(), title), true);
    }

    /**
     * Novo acesso à sala com a sessão da página, sem código: é o que faz o F5 (ou cair a conexão) não pedir outro
     * código. Vale enquanto a pessoa continuar no canal de voz; também atualiza a permissão de transmitir (quem
     * abriu uma stream depois de entrar passa a poder compartilhar a tela).
     */
    public JoinTicket resume(String sessionToken) {
        requireConfigured();
        var session = session(sessionToken);
        var current = presence.voiceChannelOf(session.guildId(), session.userId());
        if (current.isEmpty() || current.get() != session.voiceChannelId()) {
            sessions.revoke(session.guildId(), session.voiceChannelId(), session.userId());
            throw new UserFacingException("Você saiu do canal de voz da stream. Entre de novo e peça outro código.");
        }
        var stream = Optional.ofNullable(live.get(session.streamId()))
                .or(() -> streamsIn(session.guildId(), session.voiceChannelId()).stream().findFirst())
                .orElse(null);
        if (stream == null && session.streamId() != NO_STREAM) {
            throw new UserFacingException("Não há stream ao vivo neste canal agora.");
        }
        // Sessão da Atividade num canal sem stream: volta para a sala vazia
        return ticket(session.guildId(), session.voiceChannelId(), stream, session.userId(), session.displayName(),
                session.token(), session.activity());
    }

    /** Streams ao vivo no canal de voz da sessão (a página usa para dar nome às telas e montar a lista). */
    public List<StreamSession> channelStreams(String sessionToken) {
        var session = session(sessionToken);
        return streamsIn(session.guildId(), session.voiceChannelId());
    }

    /**
     * Encerra uma stream. Só quem transmite ou um admin do bot. Quem transmitia perde a permissão de
     * transmitir, mas continua na sala assistindo às outras streams do canal.
     */
    public StreamSession end(long streamId, long userId) {
        var stream = Optional.ofNullable(live.get(streamId))
                .orElseThrow(() -> new UserFacingException("Esta stream já terminou."));
        if (stream.hostUserId() != userId && !admins.isAdmin(userId)) {
            throw new UserFacingException("Só quem abriu a stream pode encerrá-la.");
        }
        live.remove(streamId);
        identitiesOf(stream.hostUserId()).forEach(identity -> rooms.revokePublish(stream.room(), identity));
        rooms.broadcast(stream.room(), STREAMS_CHANGED);
        return stream;
    }

    /** Encerra a stream que a pessoa está transmitindo neste servidor. */
    public StreamSession endOwn(long guildId, long userId) {
        var stream = hostedBy(guildId, userId)
                .orElseThrow(() -> new UserFacingException("Você não tem nenhuma stream aberta."));
        return end(stream.id(), userId);
    }

    /**
     * A pessoa saiu (ou trocou) do canal de voz {@code leftVoiceChannelId}: os códigos pendentes dela deixam de
     * valer, ela é desconectada da sala daquele canal e a stream que ela transmitia lá termina. Devolve as streams
     * encerradas, para o adaptador avisar o canal de texto.
     */
    public List<StreamSession> leftVoiceChannel(long guildId, long userId, long leftVoiceChannelId) {
        codes.revoke(guildId, userId);
        sessions.revoke(guildId, leftVoiceChannelId, userId);
        var ended = streamsIn(guildId, leftVoiceChannelId).stream()
                .filter(stream -> stream.hostUserId() == userId)
                .toList();
        ended.forEach(stream -> live.remove(stream.id()));
        if (media.isConfigured()) {
            var room = StreamSession.roomOf(guildId, leftVoiceChannelId);
            identitiesOf(userId).forEach(identity -> rooms.remove(room, identity));
            if (!ended.isEmpty()) {
                rooms.broadcast(room, STREAMS_CHANGED);
            }
        }
        return ended;
    }

    public Optional<StreamSession> get(long streamId) {
        return Optional.ofNullable(live.get(streamId));
    }

    public List<StreamSession> streamsIn(long guildId, long voiceChannelId) {
        return live.values().stream()
                .filter(stream -> stream.guildId() == guildId && stream.voiceChannelId() == voiceChannelId)
                .sorted(Comparator.comparingLong(StreamSession::id))
                .toList();
    }

    public List<StreamSession> streamsInGuild(long guildId) {
        return live.values().stream()
                .filter(stream -> stream.guildId() == guildId)
                .sorted(Comparator.comparingLong(StreamSession::id))
                .toList();
    }

    public String linkOf(StreamSession stream) {
        return publicUrl + "/stream/" + stream.id();
    }

    /**
     * IDs no formato snowflake (biblioteca snowflake-id): 41 bits de tempo, 10 de nó e 12 de sequência.
     * O relógio é monotônico (não repete ID se o relógio do sistema voltar) e, se a sequência de um
     * milissegundo esgotar, espera o próximo em vez de falhar.
     */
    static LongSupplier snowflakes(int node) {
        var generator = SnowflakeIdGenerator.createCustom(node, new MonotonicTimeSource(ID_EPOCH),
                Structure.createDefault(), new Options(Options.SequenceOverflowStrategy.SPIN_WAIT));
        return generator::next;
    }

    private Optional<StreamSession> hostedBy(long guildId, long userId) {
        return live.values().stream()
                .filter(stream -> stream.guildId() == guildId && stream.hostUserId() == userId)
                .findFirst();
    }

    /**
     * Identidade na sala do LiveKit. A Atividade e o navegador da mesma pessoa ficam na sala ao mesmo tempo (ex.:
     * assiste pela Atividade e transmite pelo navegador), e o LiveKit derruba quem entra com uma identidade repetida;
     * então a Atividade ganha um sufixo. A página trata as duas como a mesma pessoa (o id antes do {@code #}).
     */
    static String identityOf(long userId, boolean activity) {
        return activity ? userId + ACTIVITY_IDENTITY : String.valueOf(userId);
    }

    private static List<String> identitiesOf(long userId) {
        return List.of(identityOf(userId, false), identityOf(userId, true));
    }

    /** O token diz se a pessoa pode transmitir e, no metadata, qual stream é a dela (a página usa para dar nome à tela). */
    private JoinTicket ticket(long guildId, long voiceChannelId, StreamSession stream, long userId, String displayName,
                              String sessionToken, boolean activity) {
        var channelStreams = streamsIn(guildId, voiceChannelId);
        var hosted = channelStreams.stream().filter(s -> s.hostUserId() == userId).findFirst();
        var identity = identityOf(userId, activity);
        var room = StreamSession.roomOf(guildId, voiceChannelId);
        var token = media.token(room, identity, displayName, hosted.isPresent(),
                hosted.map(s -> String.valueOf(s.id())).orElse(""));
        return new JoinTicket(media.serverUrl(), token, room, identity, hosted.isPresent(), stream, channelStreams,
                sessionToken);
    }

    /** A sessão, desde que a pessoa continue no canal de voz dela (senão a sessão é revogada). */
    private ViewerSessions.ViewerSession sessionInItsChannel(String token) {
        var session = session(token);
        var current = presence.voiceChannelOf(session.guildId(), session.userId());
        if (current.isEmpty() || current.get() != session.voiceChannelId()) {
            sessions.revoke(session.guildId(), session.voiceChannelId(), session.userId());
            throw new UserFacingException("Você saiu do canal de voz. Entre de novo e abra a Atividade.");
        }
        return session;
    }

    private ViewerSessions.ViewerSession session(String token) {
        return sessions.get(token)
                .orElseThrow(() -> new UserFacingException("Sua sessão terminou. Peça um novo código com /stream join."));
    }

    private Invite invite(StreamSession stream, long userId, String displayName) {
        var code = codes.issue(stream.id(), stream.guildId(), stream.voiceChannelId(), userId, displayName);
        return new Invite(stream, linkOf(stream), code);
    }

    private long currentVoiceChannel(long guildId, long userId) {
        return presence.voiceChannelOf(guildId, userId)
                .orElseThrow(() -> new UserFacingException("Entre em um canal de voz primeiro."));
    }

    private void requireConfigured() {
        if (!media.isConfigured()) {
            throw new UserFacingException("O streaming ainda não foi configurado neste bot (faltam as credenciais do LiveKit).");
        }
    }

    private static Optional<StreamSession> find(List<StreamSession> streams, String query) {
        if (query == null || query.isBlank()) {
            return streams.stream().findFirst();
        }
        var needle = query.strip().toLowerCase(Locale.ROOT);
        return streams.stream().filter(s -> String.valueOf(s.id()).equals(needle)).findFirst()
                .or(() -> streams.stream().filter(s -> s.title().equalsIgnoreCase(needle)).findFirst())
                .or(() -> streams.stream().filter(s -> String.valueOf(s.hostUserId()).equals(needle)).findFirst())
                .or(() -> streams.stream().filter(s -> s.hostName().equalsIgnoreCase(needle)).findFirst())
                .or(() -> streams.stream().filter(s -> s.title().toLowerCase(Locale.ROOT).contains(needle)
                        || s.hostName().toLowerCase(Locale.ROOT).contains(needle)).findFirst());
    }
}
