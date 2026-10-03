package com.gabriellpa.sabadaco.music;

/**
 * Porta para a conexão de voz. O núcleo de música não conhece o Discord; quem implementa é o adaptador JDA.
 */
public interface VoiceGateway {

    void connect(long guildId, long voiceChannelId, GuildPlayer player);

    void disconnect(long guildId);

    boolean isConnected(long guildId);
}
