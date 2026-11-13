package com.gabriellpa.sabadaco.stream;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sessões da página da stream: um token <b>opaco</b> que a página recebe ao trocar o código e usa para o chat e
 * para listar as streams do canal. Fica só na memória do bot, então pode ser revogado na hora (saiu do canal de
 * voz), ao contrário do JWT do LiveKit, que vale até expirar.
 */
public class ViewerSessions {

    /** Teto de segurança; na prática a sessão acaba antes, quando a pessoa sai do canal de voz. */
    static final Duration MAX_AGE = Duration.ofHours(12);

    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, ViewerSession> sessions = new ConcurrentHashMap<>();

    public ViewerSessions(Clock clock) {
        this.clock = clock;
    }

    /**
     * @param streamId a stream pela qual a pessoa entrou (o chat dela vai para o canal de texto dessa stream)
     * @param activity entrou pela Atividade do Discord (e não pelo link do navegador)
     */
    public record ViewerSession(String token, long streamId, long guildId, long voiceChannelId, long userId,
                                String displayName, Instant createdAt, boolean activity) {
    }

    public ViewerSession open(long streamId, long guildId, long voiceChannelId, long userId, String displayName) {
        return open(streamId, guildId, voiceChannelId, userId, displayName, false);
    }

    public ViewerSession open(long streamId, long guildId, long voiceChannelId, long userId, String displayName,
                              boolean activity) {
        var bytes = new byte[32];
        random.nextBytes(bytes);
        var token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        var session = new ViewerSession(token, streamId, guildId, voiceChannelId, userId, displayName, clock.instant(),
                activity);
        sessions.put(token, session);
        return session;
    }

    public Optional<ViewerSession> get(String token) {
        if (token == null) {
            return Optional.empty();
        }
        var session = sessions.get(token);
        if (session != null && session.createdAt().plus(MAX_AGE).isBefore(clock.instant())) {
            sessions.remove(token);
            return Optional.empty();
        }
        return Optional.ofNullable(session);
    }

    /** Encerra as sessões da pessoa no canal de voz (ex.: saiu dele). */
    public void revoke(long guildId, long voiceChannelId, long userId) {
        sessions.values().removeIf(s -> s.guildId() == guildId && s.voiceChannelId() == voiceChannelId && s.userId() == userId);
    }
}
