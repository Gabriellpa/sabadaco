package com.gabriellpa.sabadaco.discord;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Atividade do Discord (a stream aberta dentro do Discord).
 *
 * @param clientId     ID do app; vazio = o ID do próprio bot (é o mesmo nos apps atuais)
 * @param clientSecret OAuth2 → Client Secret do app; sem ele a Atividade fica desativada
 */
@ConfigurationProperties(prefix = "discord.activity")
public record DiscordActivityProperties(String clientId, String clientSecret) {
}
