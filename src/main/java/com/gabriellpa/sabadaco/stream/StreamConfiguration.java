package com.gabriellpa.sabadaco.stream;

import com.gabriellpa.sabadaco.DiscordAdmins;
import com.gabriellpa.sabadaco.stream.livekit.LiveKitTokenIssuer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(StreamProperties.class)
public class StreamConfiguration {

    /**
     * Implementação local dos códigos. Para usar uma API externa no futuro, troque só este bean por uma
     * implementação de {@link JoinCodeIssuer} que chame a API; o resto não muda.
     */
    @Bean
    public JoinCodeIssuer joinCodeIssuer(StreamProperties properties) {
        return new InMemoryJoinCodeIssuer(Clock.systemUTC(), properties.codeTtl());
    }

    @Bean
    public MediaTokenIssuer mediaTokenIssuer(StreamProperties properties) {
        return new LiveKitTokenIssuer(properties.livekit());
    }

    @Bean
    public StreamService streamService(StreamProperties properties, JoinCodeIssuer codes, MediaTokenIssuer media,
                                       VoicePresence presence, DiscordAdmins admins) {
        var clock = Clock.systemUTC();
        return new StreamService(new SnowflakeGenerator(clock, properties.node()), codes, media, presence, admins,
                clock, properties.publicUrl());
    }
}
