package com.gabriellpa.sabadaco.stream.livekit;

import com.gabriellpa.sabadaco.stream.StreamProperties;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class LiveKitTokenIssuerTest {

    private final LiveKitTokenIssuer issuer = new LiveKitTokenIssuer(
            new StreamProperties.LiveKit("wss://x.livekit.cloud", "APIkey", "secret-com-pelo-menos-32-caracteres!!", Duration.ofHours(6)));

    @Test
    void viewerCanOnlySubscribe() {
        var claims = payload(issuer.token("g1-c2", "42", "gabriel", false));

        assertThat(claims).contains("\"sub\":\"42\"", "\"name\":\"gabriel\"", "\"room\":\"g1-c2\"",
                "\"roomJoin\":true", "\"canSubscribe\":true", "\"canPublish\":false", "\"canPublishData\":false");
        assertThat(claims).doesNotContain("canPublishSources");
    }

    @Test
    void hostCanPublishOnlyScreen() {
        var claims = payload(issuer.token("g1-c2", "42", "gabriel", true));

        assertThat(claims).contains("\"canPublish\":true", "\"canPublishSources\":[\"screen_share\",\"screen_share_audio\"]");
    }

    @Test
    void notConfiguredWithoutCredentials() {
        assertThat(issuer.isConfigured()).isTrue();
        assertThat(new LiveKitTokenIssuer(new StreamProperties.LiveKit("", null, null, Duration.ofHours(1))).isConfigured()).isFalse();
    }

    private static String payload(String jwt) {
        return new String(Base64.getUrlDecoder().decode(jwt.split("\\.")[1]), StandardCharsets.UTF_8);
    }
}
