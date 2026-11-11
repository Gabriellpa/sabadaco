package com.gabriellpa.sabadaco.stream;

import com.gabriellpa.sabadaco.stream.livekit.LiveKitRooms;
import com.gabriellpa.sabadaco.stream.livekit.LiveKitTokenIssuer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Implementações das portas do streaming (o {@link StreamService} é um {@code @Service}). */
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
    public MediaRooms mediaRooms(StreamProperties properties) {
        return new LiveKitRooms(properties.livekit());
    }
}
