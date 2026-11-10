package com.gabriellpa.sabadaco.stream;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SnowflakeGeneratorTest {

    private static final Instant START = Instant.parse("2026-11-10T20:00:00Z");

    private final MutableClock clock = new MutableClock(START);
    private final SnowflakeGenerator generator = new SnowflakeGenerator(clock, 3);

    @Test
    void carriesTheCreationTime() {
        assertThat(SnowflakeGenerator.timestampOf(generator.next())).isEqualTo(START);
    }

    @Test
    void isUniqueAndIncreasingWithinTheSameMillisecond() {
        var seen = new HashSet<Long>();
        long previous = -1;
        for (int i = 0; i < 10_000; i++) { // mais que as 4096 sequências de um ms
            long id = generator.next();
            assertThat(id).isGreaterThan(previous);
            assertThat(seen.add(id)).isTrue();
            previous = id;
        }
    }

    @Test
    void doesNotRepeatWhenTheClockGoesBack() {
        long first = generator.next();
        clock.advance(Duration.ofSeconds(-5));

        assertThat(generator.next()).isGreaterThan(first);
    }

    @Test
    void rejectsNodeOutOfRange() {
        assertThatThrownBy(() -> new SnowflakeGenerator(clock, 1024)).isInstanceOf(IllegalArgumentException.class);
    }
}
