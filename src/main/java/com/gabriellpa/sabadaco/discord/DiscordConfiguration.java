package com.gabriellpa.sabadaco.discord;

import club.minnced.discord.jdave.interop.JDaveSessionFactory;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.audio.AudioModuleConfig;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.EnumSet;
import java.util.List;

@Configuration
@EnableConfigurationProperties({DiscordProperties.class, DiscordActivityProperties.class})
public class DiscordConfiguration {

    @Bean(destroyMethod = "shutdown")
    public JDA jda(DiscordProperties properties, List<ListenerAdapter> listeners) {
        var intents = EnumSet.of(GatewayIntent.GUILD_VOICE_STATES);
        if (properties.messageContent()) {
            // Chat da stream: espelha na página o que é escrito no canal de texto. Sem a intent ligada no
            // Developer Portal o login falha (4014), por isso é opcional.
            intents.add(GatewayIntent.GUILD_MESSAGES);
            intents.add(GatewayIntent.MESSAGE_CONTENT);
        }
        return JDABuilder.createLight(properties.token(), intents)
                // Necessário para saber em qual canal de voz o usuário está
                .enableCache(CacheFlag.VOICE_STATE)
                .setMemberCachePolicy(MemberCachePolicy.VOICE)
                // DAVE (criptografia ponta-a-ponta) é obrigatório nas conexões de voz desde 01/03/2026
                .setAudioModuleConfig(new AudioModuleConfig().withDaveSessionFactory(new JDaveSessionFactory()))
                .addEventListeners(listeners.toArray())
                .build();
    }
}
