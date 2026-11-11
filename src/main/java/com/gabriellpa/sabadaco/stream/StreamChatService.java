package com.gabriellpa.sabadaco.stream;

import com.gabriellpa.sabadaco.UserFacingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Chat espelhado entre a página da stream e o canal de texto onde o {@code /stream criar} foi usado.
 * <ul>
 *   <li>Página → Discord: a página manda pelo bot ({@link ChatRelay}); a mensagem volta para a página pelo
 *       caminho Discord → página, então todo mundo vê a mesma ordem.</li>
 *   <li>Discord → página: o bot repassa as mensagens do canal de texto para as salas das streams dele.</li>
 * </ul>
 */
@Service
public class StreamChatService {

    static final int MAX_LENGTH = 500;
    static final Duration MIN_INTERVAL = Duration.ofMillis(700);

    private final StreamService streams;
    private final ViewerSessions sessions;
    private final VoicePresence presence;
    private final ChatRelay relay;
    private final MediaRooms rooms;
    private final JsonMapper json;
    private final Clock clock;
    private final Map<String, Instant> lastMessage = new ConcurrentHashMap<>();
    private final AtomicLong webMessages = new AtomicLong();

    @Autowired
    public StreamChatService(StreamService streams, ViewerSessions sessions, VoicePresence presence, ChatRelay relay,
                             MediaRooms rooms, JsonMapper json) {
        this(streams, sessions, presence, relay, rooms, json, Clock.systemUTC());
    }

    StreamChatService(StreamService streams, ViewerSessions sessions, VoicePresence presence, ChatRelay relay,
                      MediaRooms rooms, JsonMapper json, Clock clock) {
        this.streams = streams;
        this.sessions = sessions;
        this.presence = presence;
        this.relay = relay;
        this.rooms = rooms;
        this.json = json;
        this.clock = clock;
    }

    /**
     * Mensagem escrita na página. Vai para o canal de texto da stream pela qual a pessoa entrou e, na hora, para as
     * salas das streams desse canal (o bot ignora o próprio eco no Discord, então ninguém vê a mensagem duas vezes).
     */
    public void fromWeb(String sessionToken, String text) {
        var session = session(sessionToken);
        var content = text == null ? "" : text.strip();
        if (content.isEmpty()) {
            throw new UserFacingException("Escreva alguma coisa.");
        }
        if (content.length() > MAX_LENGTH) {
            throw new UserFacingException("A mensagem pode ter até " + MAX_LENGTH + " caracteres.");
        }
        var current = presence.voiceChannelOf(session.guildId(), session.userId());
        if (current.isEmpty() || current.get() != session.voiceChannelId()) {
            throw new UserFacingException("Você saiu do canal de voz da stream.");
        }
        var now = clock.instant();
        var previous = lastMessage.put(session.token(), now);
        if (previous != null && previous.plus(MIN_INTERVAL).isAfter(now)) {
            throw new UserFacingException("Calma, uma mensagem de cada vez.");
        }
        var textChannelId = streams.get(session.streamId())
                .or(() -> streams.streamsIn(session.guildId(), session.voiceChannelId()).stream().findFirst())
                .map(StreamSession::textChannelId)
                .orElseThrow(() -> new UserFacingException("Não há stream ao vivo neste canal agora."));
        relay.postToDiscord(session.guildId(), textChannelId, session.userId(), session.displayName(), content);
        broadcast(session.guildId(), textChannelId, ChatMessage.of("w" + webMessages.incrementAndGet(),
                session.displayName(), relay.avatarUrl(session.guildId(), session.userId()), content, now.toEpochMilli(), true));
    }

    /**
     * Mensagem do canal de texto: vai para a sala de cada stream ao vivo criada nesse canal.
     *
     * @return se havia alguma stream para receber (o adaptador pode usar para não fazer trabalho à toa)
     */
    public boolean fromDiscord(long guildId, long textChannelId, String messageId, String author, String avatarUrl,
                               String content, Instant sentAt) {
        if (content == null || content.isBlank()) {
            return false;
        }
        return broadcast(guildId, textChannelId, ChatMessage.of(messageId, author, avatarUrl, content, sentAt.toEpochMilli(), false));
    }

    /** Se o canal de texto tem stream ao vivo (o listener do Discord olha isso antes de ler a mensagem). */
    public boolean mirrors(long guildId, long textChannelId) {
        return !roomsOf(guildId, textChannelId).isEmpty();
    }

    private ViewerSessions.ViewerSession session(String token) {
        return sessions.get(token)
                .orElseThrow(() -> new UserFacingException("Sua sessão terminou. Peça um novo código com /stream join."));
    }

    private List<String> roomsOf(long guildId, long textChannelId) {
        return streams.streamsInGuild(guildId).stream()
                .filter(stream -> stream.textChannelId() == textChannelId)
                .map(StreamSession::room)
                .distinct()
                .toList();
    }

    private boolean broadcast(long guildId, long textChannelId, ChatMessage message) {
        var targets = roomsOf(guildId, textChannelId);
        if (targets.isEmpty()) {
            return false;
        }
        var data = json.writeValueAsString(message).getBytes(StandardCharsets.UTF_8);
        targets.forEach(room -> rooms.broadcast(room, data));
        return true;
    }
}
