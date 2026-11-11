package com.gabriellpa.sabadaco.discord.ui;

import com.gabriellpa.sabadaco.stream.StreamSession;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class StreamCardTest {

    private static final Instant START = Instant.parse("2026-11-10T22:00:00Z");
    private final StreamSession stream = new StreamSession(123, "Valorant *ranked*", 10, 20, 30, 1, "gabriel", START);

    @Test
    void liveCardHasTitleHostChannelAndJoinButton() {
        var json = Messages.create(StreamCard.live(stream, "https://cdn.example/avatar.png")).toData().toString();

        assertThat(json).contains("AO VIVO", "Valorant \\\\*ranked\\\\*", "<@1>", "<#20>", "<t:" + START.getEpochSecond() + ":R>",
                "\"custom_id\":\"stream:join:123\"", "Assistir", "avatar.png");
    }

    @Test
    void endedCardHasNoButtonAndSaysWhyAndHowLong() {
        var json = Messages.edit(StreamCard.ended(stream, null, "quem transmitia saiu do canal de voz", START.plusSeconds(3725)))
                .toData().toString();

        assertThat(json).contains("Stream encerrada", "Quem transmitia saiu do canal de voz", Format.duration(3_725_000));
        assertThat(json).doesNotContain("custom_id");
    }
}
