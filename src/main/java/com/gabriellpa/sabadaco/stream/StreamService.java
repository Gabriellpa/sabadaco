package com.gabriellpa.sabadaco.stream;

import com.gabriellpa.sabadaco.DiscordAdmins;
import com.gabriellpa.sabadaco.UserFacingException;
import de.mkammerer.snowflakeid.SnowflakeIdGenerator;
import de.mkammerer.snowflakeid.options.Options;
import de.mkammerer.snowflakeid.structure.Structure;
import de.mkammerer.snowflakeid.time.MonotonicTimeSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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

    private final LongSupplier ids;
    private final JoinCodeIssuer codes;
    private final MediaTokenIssuer media;
    private final MediaRooms rooms;
    private final VoicePresence presence;
    private final DiscordAdmins admins;
    private final Clock clock;
    private final String publicUrl;
    private final Map<Long, StreamSession> live = new ConcurrentHashMap<>();

    /** Início da contagem dos IDs snowflake (como o 2015-01-01 do Discord). */
    static final Instant ID_EPOCH = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired
    public StreamService(StreamProperties properties, JoinCodeIssuer codes, MediaTokenIssuer media, MediaRooms rooms,
                         VoicePresence presence, DiscordAdmins admins) {
        this(snowflakes(properties.node()), codes, media, rooms, presence, admins, Clock.systemUTC(),
                properties.publicUrl());
    }

    StreamService(LongSupplier ids, JoinCodeIssuer codes, MediaTokenIssuer media, MediaRooms rooms,
                  VoicePresence presence, DiscordAdmins admins, Clock clock, String publicUrl) {
        this.ids = ids;
        this.codes = codes;
        this.media = media;
        this.rooms = rooms;
        this.presence = presence;
        this.admins = admins;
        this.clock = clock;
        this.publicUrl = publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;
    }

    /** Link e código que vão por DM. */
    public record Invite(StreamSession stream, String link, JoinCodeIssuer.JoinCode code) {
    }

    /** O que o navegador recebe para conectar na sala de mídia. */
    public record JoinTicket(String serverUrl, String token, String room, String identity, boolean canPublish,
                             StreamSession stream, List<StreamSession> channelStreams) {
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
        var channelStreams = streamsIn(stream.guildId(), stream.voiceChannelId());
        boolean canPublish = channelStreams.stream().anyMatch(s -> s.hostUserId() == grant.userId());
        var identity = String.valueOf(grant.userId());
        var token = media.token(stream.room(), identity, grant.displayName(), canPublish);
        return new JoinTicket(media.serverUrl(), token, stream.room(), identity, canPublish, stream, channelStreams);
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
        rooms.revokePublish(stream.room(), String.valueOf(stream.hostUserId()));
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
        var ended = streamsIn(guildId, leftVoiceChannelId).stream()
                .filter(stream -> stream.hostUserId() == userId)
                .toList();
        ended.forEach(stream -> live.remove(stream.id()));
        if (media.isConfigured()) {
            rooms.remove(StreamSession.roomOf(guildId, leftVoiceChannelId), String.valueOf(userId));
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
