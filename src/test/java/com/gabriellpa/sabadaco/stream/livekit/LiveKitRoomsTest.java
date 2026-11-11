package com.gabriellpa.sabadaco.stream.livekit;

import com.gabriellpa.sabadaco.stream.StreamProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class LiveKitRoomsTest {

    @Test
    void serverApiUsesHttpOnTheSameHost() {
        assertThat(LiveKitRooms.httpUrl("wss://x.livekit.cloud")).isEqualTo("https://x.livekit.cloud");
        assertThat(LiveKitRooms.httpUrl("ws://localhost:7880")).isEqualTo("http://localhost:7880");
    }

    @Test
    void endingAStreamLeavesTheHostAbleToWatchOnly() {
        assertThat(LiveKitRooms.VIEW_ONLY.getCanSubscribe()).isTrue();
        assertThat(LiveKitRooms.VIEW_ONLY.getCanPublish()).isFalse();
        assertThat(LiveKitRooms.VIEW_ONLY.getCanPublishData()).isFalse();
    }

    @Test
    void doesNothingWhenNotConfigured() {
        var rooms = new LiveKitRooms(new StreamProperties.LiveKit("", null, null, Duration.ofMinutes(5)));

        assertThatCode(() -> {
            rooms.remove("g1-c2", "42");
            rooms.revokePublish("g1-c2", "42");
        }).doesNotThrowAnyException();
    }

    @Test
    void networkFailuresAreLoggedNotThrown() {
        // Porta fechada: a chamada falha, mas a regra (encerrar/remover) não pode quebrar por isso
        var rooms = new LiveKitRooms(new StreamProperties.LiveKit("ws://127.0.0.1:1", "key", "secret-com-pelo-menos-32-caracteres!!", Duration.ofMinutes(5)));

        assertThatCode(() -> rooms.remove("g1-c2", "42")).doesNotThrowAnyException();
    }
}
