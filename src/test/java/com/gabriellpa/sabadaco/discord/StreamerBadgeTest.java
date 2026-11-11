package com.gabriellpa.sabadaco.discord;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StreamerBadgeTest {

    @Test
    void addsTheBadgeInFrontOfTheName() {
        assertThat(StreamerBadge.withBadge("gabriel")).isEqualTo("🔴 gabriel");
    }

    @Test
    void cutsLongNamesToTheDiscordLimitWithoutBreakingCharacters() {
        var badged = StreamerBadge.withBadge("x".repeat(40));
        assertThat(badged).startsWith(StreamerBadge.BADGE).hasSizeLessThanOrEqualTo(StreamerBadge.MAX_NICKNAME);

        var emojis = StreamerBadge.withBadge("😀".repeat(20));
        assertThat(emojis.length()).isLessThanOrEqualTo(StreamerBadge.MAX_NICKNAME);
        assertThat(Character.isHighSurrogate(emojis.charAt(emojis.length() - 1))).isFalse();
    }

    @Test
    void removingTheBadgeRestoresTheNameOrClearsTheNickname() {
        assertThat(StreamerBadge.withoutBadge("🔴 gabriel")).isEqualTo("gabriel");
        assertThat(StreamerBadge.withoutBadge("🔴 ")).isNull();
        assertThat(StreamerBadge.withoutBadge("gabriel")).isEqualTo("gabriel");
    }
}
