package com.gabriellpa.sabadaco.stream;

import com.gabriellpa.sabadaco.stream.JoinCodeIssuer.JoinGrant;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryJoinCodeIssuerTest {

    private static final long GUILD = 10;
    private static final long VOICE = 20;
    private static final long ALICE = 1;
    private static final long BOB = 2;

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-10T20:00:00Z"));
    private final InMemoryJoinCodeIssuer issuer = new InMemoryJoinCodeIssuer(clock, Duration.ofMinutes(5));

    @Test
    void codeLooksLikeABCD_EFGH() {
        var code = issuer.issue(GUILD, VOICE, ALICE, "alice");

        assertThat(code.code()).matches("[" + InMemoryJoinCodeIssuer.ALPHABET + "]{4}-[" + InMemoryJoinCodeIssuer.ALPHABET + "]{4}");
        assertThat(code.expiresAt()).isEqualTo(clock.instant().plus(Duration.ofMinutes(5)));
    }

    @Test
    void redeemsOnceWithTheGrant() {
        var code = issuer.issue(GUILD, VOICE, ALICE, "alice").code();

        assertThat(issuer.redeem(code)).contains(new JoinGrant(GUILD, VOICE, ALICE, "alice"));
        assertThat(issuer.redeem(code)).isEmpty();
    }

    @Test
    void acceptsLowercaseAndWithoutHyphen() {
        var code = issuer.issue(GUILD, VOICE, ALICE, "alice").code();

        assertThat(issuer.redeem(" " + code.replace("-", "").toLowerCase() + " ")).isPresent();
    }

    @Test
    void expiresAfterTtl() {
        var code = issuer.issue(GUILD, VOICE, ALICE, "alice").code();
        clock.advance(Duration.ofMinutes(5));

        assertThat(issuer.redeem(code)).isEmpty();
    }

    @Test
    void newCodeInvalidatesThePreviousOneOfTheSamePerson() {
        var first = issuer.issue(GUILD, VOICE, ALICE, "alice").code();
        var bobs = issuer.issue(GUILD, VOICE, BOB, "bob").code();
        var second = issuer.issue(GUILD, VOICE, ALICE, "alice").code();

        assertThat(issuer.redeem(first)).isEmpty();
        assertThat(issuer.redeem(second)).isPresent();
        assertThat(issuer.redeem(bobs)).isPresent();
    }

    @Test
    void revokeDropsPendingCodes() {
        var code = issuer.issue(GUILD, VOICE, ALICE, "alice").code();
        issuer.revoke(GUILD, ALICE);

        assertThat(issuer.redeem(code)).isEmpty();
    }

    @Test
    void expiredCodesAreCleanedUp() {
        issuer.issue(GUILD, VOICE, ALICE, "alice");
        clock.advance(Duration.ofMinutes(6));
        issuer.issue(GUILD, VOICE, BOB, "bob");

        assertThat(issuer.size()).isEqualTo(1);
    }

    @Test
    void unknownOrNullCodeIsRejected() {
        assertThat(issuer.redeem("AAAA-AAAA")).isEmpty();
        assertThat(issuer.redeem(null)).isEmpty();
    }
}
