package com.gabriellpa.sabadaco.bot.discord.configuration;

import com.gabriellpa.sabadaco.bot.discord.commands.CommandRegistry;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@RequiredArgsConstructor
public class DiscordConfiguration {

    private final DiscordConfigurationProperties discordConfigurationProperties;

    @Bean
    public JDA jda(CommandRegistry registry, List<ListenerAdapter> listeners) throws Exception {
        var jda = JDABuilder.createDefault(discordConfigurationProperties.token()).build();
        registry.registerCommandsWithJDA(jda);
        listeners.forEach(jda::addEventListener);

        return jda;
    }

}
