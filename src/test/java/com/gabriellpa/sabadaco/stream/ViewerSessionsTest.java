package com.gabriellpa.sabadaco.stream;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ViewerSessionsTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-10T20:00:00Z"));
    private final ViewerSessions sessions = new ViewerSessions(clock);

    @Test
    void opaqueRandomTokens() {
        var first = sessions.open(1, 10, 20, 2, "viewer");
        var second = sessions.open(1, 10, 20, 2, "viewer");

        assertThat(first.token()).hasSize(43).doesNotContain("viewer", "=").isNotEqualTo(second.token());
        assertThat(sessions.get(first.token())).contains(first);
        assertThat(sessions.get("inventado")).isEmpty();
        assertThat(sessions.get(null)).isEmpty();
    }

    @Test
    void revokeOnlyTouchesThatPersonInThatChannel() {
        var mine = sessions.open(1, 10, 20, 2, "viewer");
        var otherChannel = sessions.open(5, 10, 21, 2, "viewer");
        var someoneElse = sessions.open(1, 10, 20, 3, "outro");

        sessions.revoke(10, 20, 2);

        assertThat(sessions.get(mine.token())).isEmpty();
        assertThat(sessions.get(otherChannel.token())).isPresent();
        assertThat(sessions.get(someoneElse.token())).isPresent();
    }

    @Test
    void expiresAfterMaxAge() {
        var session = sessions.open(1, 10, 20, 2, "viewer");
        clock.advance(ViewerSessions.MAX_AGE.plus(Duration.ofSeconds(1)));

        assertThat(sessions.get(session.token())).isEmpty();
    }
}
