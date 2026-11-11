package com.gabriellpa.sabadaco.stream.livekit;

import com.gabriellpa.sabadaco.stream.MediaTokenIssuer;
import com.gabriellpa.sabadaco.stream.StreamProperties;
import io.livekit.server.AccessToken;
import io.livekit.server.CanPublish;
import io.livekit.server.CanPublishData;
import io.livekit.server.CanPublishSources;
import io.livekit.server.CanSubscribe;
import io.livekit.server.RoomJoin;
import io.livekit.server.RoomName;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * Tokens de acesso do LiveKit (JWT assinado com a API secret). O navegador conecta direto no LiveKit com ele;
 * o bot nunca passa vídeo.
 *
 * <p>Quem transmite só pode publicar fontes de tela ({@code screen_share} e o áudio dela), nunca câmera ou
 * microfone: a voz continua no Discord. O chat passa pelo bot, então ninguém publica dados direto na sala.
 */
@Slf4j
public class LiveKitTokenIssuer implements MediaTokenIssuer {

    static final List<String> SCREEN_SOURCES = List.of("screen_share", "screen_share_audio");

    private final StreamProperties.LiveKit properties;

    public LiveKitTokenIssuer(StreamProperties.LiveKit properties) {
        this.properties = properties;
        if (!isConfigured()) {
            log.warn("LiveKit não configurado (LIVEKIT_URL, LIVEKIT_API_KEY, LIVEKIT_API_SECRET): /stream fica desativado.");
        }
    }

    @Override
    public String token(String room, String identity, String displayName, boolean canPublish, String metadata) {
        var token = new AccessToken(properties.apiKey(), properties.apiSecret());
        token.setIdentity(identity);
        token.setName(displayName);
        if (metadata != null && !metadata.isEmpty()) {
            token.setMetadata(metadata);
        }
        token.setTtl(properties.tokenTtl().toMillis());
        token.addGrants(new RoomJoin(true), new RoomName(room), new CanSubscribe(true),
                new CanPublish(canPublish), new CanPublishData(false));
        if (canPublish) {
            token.addGrants(new CanPublishSources(SCREEN_SOURCES));
        }
        return token.toJwt();
    }

    @Override
    public String serverUrl() {
        return properties.url();
    }

    @Override
    public boolean isConfigured() {
        return notBlank(properties.url()) && notBlank(properties.apiKey()) && notBlank(properties.apiSecret());
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
