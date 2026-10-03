package com.gabriellpa.sabadaco.music.lavaplayer;

import com.gabriellpa.sabadaco.metrics.CountingHttpEntity;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import dev.lavalink.youtube.YoutubeSourceOptions;
import dev.lavalink.youtube.clients.AndroidVr;
import dev.lavalink.youtube.clients.Ios;
import dev.lavalink.youtube.clients.Music;
import dev.lavalink.youtube.clients.Tv;
import dev.lavalink.youtube.clients.Web;
import dev.lavalink.youtube.clients.WebEmbedded;
import dev.lavalink.youtube.clients.skeleton.Client;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

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
     * A ordem importa e foi validada empiricamente (out/2026, snapshot 2be8e54):
     * <ul>
     *     <li><b>Busca</b> para no primeiro client que responder, mesmo "sem resultados": o IOS responde vazio,
     *     então o WEB (que busca bem) precisa vir antes dele.</li>
     *     <li><b>Reprodução</b> tenta o próximo client quando um falha: desde ago/2026 o YouTube exige login no
     *     WEB/ANDROID_VR (issue lavalink-devs/youtube-source#240), e quem toca sem login é o IOS.</li>
     *     <li>Com OAuth, o TV (único client que aceita OAuth) vai na frente.</li>
     * </ul>
     */
    private static Client[] clients(YoutubeProperties youtube) {
        var clients = new ArrayList<Client>(List.of(new Music(), new Web(), new Ios(), new AndroidVr(), new WebEmbedded()));
        if (youtube.oauthEnabled()) {
            clients.addFirst(new Tv());
        }
        return clients.toArray(Client[]::new);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
