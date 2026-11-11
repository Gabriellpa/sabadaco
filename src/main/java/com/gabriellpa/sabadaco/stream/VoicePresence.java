package com.gabriellpa.sabadaco.stream;

import java.util.Optional;

/**
 * Porta para saber em qual canal de voz alguém está. O núcleo não conhece o Discord;
 * quem implementa é o adaptador JDA ({@code JdaVoicePresence}).
 */
public interface VoicePresence {

    Optional<Long> voiceChannelOf(long guildId, long userId);
}
