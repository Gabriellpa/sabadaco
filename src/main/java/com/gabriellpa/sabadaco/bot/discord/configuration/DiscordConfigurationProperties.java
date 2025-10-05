package com.gabriellpa.sabadaco.bot.discord.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "discord")
public record DiscordConfigurationProperties(String token) {
}