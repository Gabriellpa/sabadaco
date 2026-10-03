package com.gabriellpa.sabadaco.music;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SearchServiceTest {

    private final TrackLoader loader = mock(TrackLoader.class);
    private final MutableClock clock = new MutableClock();
    private final SearchService service = new SearchService(loader, new SimpleMeterRegistry(), clock);

    @Test
    void repeatedSearchesHitTheCacheUntilItExpires() {
        var results = new LoadResult.PlaylistLoaded("results", List.of(TrackSchedulerTest.track("A"), TrackSchedulerTest.track("B")), true);
        when(loader.loadNow(any(), anyString())).thenReturn(results);

        assertThat(service.search("Kassino", 5)).hasSize(2);
        assertThat(service.search(" kassino ", 1)).hasSize(1);
        verify(loader, times(1)).loadNow(any(), anyString());

        clock.advance(Duration.ofMinutes(6));
        service.search("kassino", 5);
        verify(loader, times(2)).loadNow(any(), anyString());
    }

    @Test
    void emptyResultsAreNotCached() {
        when(loader.loadNow(any(), anyString())).thenReturn(new LoadResult.NoMatches());

        service.search("nada", 5);
        service.search("nada", 5);

        verify(loader, times(2)).loadNow(any(), anyString());
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-03T12:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
