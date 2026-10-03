package com.gabriellpa.sabadaco.music.lavaplayer;

import com.gabriellpa.sabadaco.metrics.CountingHttpEntity;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import dev.lavalink.youtube.YoutubeSourceOptions;
import dev.lavalink.youtube.clients.AndroidVr;
import dev.lavalink.youtube.clients.Music;
import dev.lavalink.youtube.clients.Tv;
import dev.lavalink.youtube.clients.Web;
import dev.lavalink.youtube.clients.WebEmbedded;
import dev.lavalink.youtube.clients.skeleton.Client;
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
        if (hasText(youtube.cipherUrl())) {
            // Recomendado hospedar o próprio https://github.com/kikkia/yt-cipher (o público não tem garantia de uptime)
            options.setRemoteCipher(youtube.cipherUrl(), hasText(youtube.cipherPassword()) ? youtube.cipherPassword() : null, null);
        }
        var youtubeSource = new YoutubeAudioSourceManager(options, clients(youtube));
        if (youtube.oauthEnabled()) {
            // Sem refresh token: o youtube-source loga um código para autorizar em https://www.google.com/device
            // e, depois, o refresh token para guardar em YOUTUBE_OAUTH_REFRESH_TOKEN
            var refreshToken = hasText(youtube.oauthRefreshToken()) ? youtube.oauthRefreshToken() : null;
            youtubeSource.useOauth2(refreshToken, refreshToken != null);
        }
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

    /**
     * Sem OAuth: os clients padrão. Com OAuth: o TV (único client que aceita OAuth) vem primeiro para tocar;
     * os demais seguem como alternativa e para a busca (o TV não busca).
     */
    private static Client[] clients(YoutubeProperties youtube) {
        if (!youtube.oauthEnabled()) {
            return YoutubeAudioSourceManager.DEFAULT_CLIENTS;
        }
        return new Client[]{new Tv(), new Music(), new Web(), new WebEmbedded(), new AndroidVr()};
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
