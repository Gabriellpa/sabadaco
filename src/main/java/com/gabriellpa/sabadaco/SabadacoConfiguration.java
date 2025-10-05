package com.gabriellpa.sabadaco;

import com.gabriellpa.sabadaco.bot.discord.configuration.DiscordConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@EnableConfigurationProperties(DiscordConfigurationProperties.class)
@Configuration
public class SabadacoConfiguration {
}
