package com.gabriellpa.sabadaco.stream;

import java.time.Instant;

/**
 * Uma transmissão de tela ao vivo. Pertence a um canal de voz: só quem está nele pode assistir.
 *
 * @param textChannelId canal de texto onde o {@code /stream criar} foi usado (o chat da stream espelha ele)
 */
public record StreamSession(long id, String title, long guildId, long voiceChannelId, long textChannelId,
                            long hostUserId, String hostName, Instant startedAt) {

    /** Sala de mídia do canal de voz: todas as streams do canal ficam na mesma sala. */
    public String room() {
        return roomOf(guildId, voiceChannelId);
    }

    public static String roomOf(long guildId, long voiceChannelId) {
        return "g" + guildId + "-c" + voiceChannelId;
    }
}
