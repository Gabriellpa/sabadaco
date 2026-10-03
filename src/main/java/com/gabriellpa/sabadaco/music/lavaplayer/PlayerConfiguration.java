package com.gabriellpa.sabadaco.music.lavaplayer;

import com.gabriellpa.sabadaco.metrics.CountingHttpEntity;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import dev.lavalink.youtube.YoutubeSourceOptions;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(YoutubeProperties.class)
public class PlayerConfiguration {

    private static final String DOWNLOAD_METRIC = "sabadaco.audio.downloaded.bytes";

    @Bean(destroyMethod = "shutdown")
    @SuppressWarnings("deprecation")
    public AudioPlayerManager audioPlayerManager(YoutubeProperties youtube, MeterRegistry meterRegistry) {
        var playerManager = new DefaultAudioPlayerManager();

        var options = new YoutubeSourceOptions()
                .setAllowSearch(true)
                .setAllowDirectVideoIds(true)
                .setAllowDirectPlaylistIds(true);
        if (youtube.cipherUrl() != null && !youtube.cipherUrl().isBlank()) {
            // Recomendado hospedar o próprio https://github.com/kikkia/yt-cipher (o público não tem garantia de uptime)
            options.setRemoteCipher(youtube.cipherUrl(), youtube.cipherPassword(), null);
        }
        var youtubeSource = new YoutubeAudioSourceManager(options, YoutubeAudioSourceManager.DEFAULT_CLIENTS);
        // O YouTube precisa ser o primeiro: o Lavaplayer usa o primeiro source que reconhecer a URL
        playerManager.registerSourceManager(youtubeSource);
        AudioSourceManagers.registerRemoteSources(playerManager,
                com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioSourceManager.class);

        // O source do YouTube não implementa HttpConfigurable, então o contador é ligado direto nele
        youtubeSource.getHttpInterfaceManager().configureBuilder(builder ->
                builder.addInterceptorLast(CountingHttpEntity.interceptor(meterRegistry.counter(DOWNLOAD_METRIC, "source", "youtube"))));
        // Demais sources (SoundCloud, Bandcamp, HTTP...): precisa ser chamado depois do registro
        playerManager.setHttpBuilderConfigurator(builder ->
                builder.addInterceptorLast(CountingHttpEntity.interceptor(meterRegistry.counter(DOWNLOAD_METRIC, "source", "other"))));
        return playerManager;
    }
}
