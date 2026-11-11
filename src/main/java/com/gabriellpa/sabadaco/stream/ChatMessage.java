package com.gabriellpa.sabadaco.stream;

/**
 * Mensagem do chat da stream, enviada para a sala de mídia como dado (o LiveKit entrega a todos na sala).
 *
 * @param fromWeb {@code true} se foi escrita na página (e espelhada no Discord pelo bot)
 */
public record ChatMessage(String type, String id, String author, String avatarUrl, String content, long timestamp,
                          boolean fromWeb) {

    public static ChatMessage of(String id, String author, String avatarUrl, String content, long timestamp, boolean fromWeb) {
        return new ChatMessage("chat", id, author, avatarUrl, content, timestamp, fromWeb);
    }
}
