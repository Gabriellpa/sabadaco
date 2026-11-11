package com.gabriellpa.sabadaco.discord;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param token      token do bot
 * @param devGuildId     se definido, os comandos são registrados só neste servidor (atualiza na hora)
 * @param messageContent lê as mensagens dos canais de texto (intent privilegiada "Message Content", precisa estar
 *                       ligada no Developer Portal). Usada para espelhar o chat do canal na página da stream.
 */
@ConfigurationProperties(prefix = "discord")
public record DiscordProperties(String token, Long devGuildId, boolean messageContent) {
}
