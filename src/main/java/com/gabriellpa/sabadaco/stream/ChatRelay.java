package com.gabriellpa.sabadaco.stream;

/**
 * Porta para o lado do Discord do chat da stream. Hoje é o bot ({@code DiscordChatRelay}); o núcleo não conhece JDA.
 */
public interface ChatRelay {

    /** Publica no canal de texto uma mensagem escrita na página (com o nome da pessoa, sem mencionar ninguém). */
    void postToDiscord(long guildId, long textChannelId, long userId, String displayName, String content);

    /** Avatar da pessoa no servidor, para mostrar na página; {@code null} se não souber. */
    String avatarUrl(long guildId, long userId);
}
