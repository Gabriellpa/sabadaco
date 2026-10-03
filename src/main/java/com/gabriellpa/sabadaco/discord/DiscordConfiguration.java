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

import java.util.List;

@Configuration
@EnableConfigurationProperties(DiscordProperties.class)
public class DiscordConfiguration {

    @Bean(destroyMethod = "shutdown")
    public JDA jda(DiscordProperties properties, List<ListenerAdapter> listeners) {
        return JDABuilder.createLight(properties.token(), GatewayIntent.GUILD_VOICE_STATES)
                // Necessário para saber em qual canal de voz o usuário está
                .enableCache(CacheFlag.VOICE_STATE)
                .setMemberCachePolicy(MemberCachePolicy.VOICE)
                // DAVE (criptografia ponta-a-ponta) é obrigatório nas conexões de voz desde 01/03/2026
                .setAudioModuleConfig(new AudioModuleConfig().withDaveSessionFactory(new JDaveSessionFactory()))
                .addEventListeners(listeners.toArray())
                .build();
    }
}
