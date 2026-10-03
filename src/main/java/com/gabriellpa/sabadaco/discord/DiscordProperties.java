package com.gabriellpa.sabadaco.discord;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param token      token do bot
 * @param devGuildId se definido, os comandos são registrados só neste servidor (atualiza na hora)
 */
@ConfigurationProperties(prefix = "discord")
public record DiscordProperties(String token, Long devGuildId) {
}
