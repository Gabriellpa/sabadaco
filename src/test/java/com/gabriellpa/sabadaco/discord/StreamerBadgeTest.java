package com.gabriellpa.sabadaco.discord;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StreamerBadgeTest {

    @Test
    void addsTheBadgeAtTheEndOfTheName() {
        assertThat(StreamerBadge.withBadge("gabriel")).isEqualTo("gabriel 🎥");
    }

    @Test
    void cutsLongNamesToTheDiscordLimitKeepingTheBadge() {
        var badged = StreamerBadge.withBadge("x".repeat(40));
        assertThat(badged).endsWith(StreamerBadge.BADGE).hasSize(StreamerBadge.MAX_NICKNAME);

        var emojis = StreamerBadge.withBadge("😀".repeat(20));
        assertThat(emojis).endsWith(StreamerBadge.BADGE);
        assertThat(emojis.length()).isLessThanOrEqualTo(StreamerBadge.MAX_NICKNAME);
        var name = emojis.substring(0, emojis.length() - StreamerBadge.BADGE.length());
        assertThat(Character.isHighSurrogate(name.charAt(name.length() - 1))).isFalse();
    }

    @Test
    void removingTheBadgeRestoresTheNameOrClearsTheNickname() {
        assertThat(StreamerBadge.withoutBadge("gabriel 🎥")).isEqualTo("gabriel");
        assertThat(StreamerBadge.withoutBadge(" 🎥")).isNull();
        assertThat(StreamerBadge.withoutBadge("gabriel")).isEqualTo("gabriel");
    }
}
