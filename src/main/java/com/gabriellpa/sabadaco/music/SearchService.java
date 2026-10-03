package com.gabriellpa.sabadaco.music;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Busca no YouTube com um cache curto: o autocomplete repete muito as mesmas buscas
 * (apagar uma letra, redigitar), e cada busca custa uma ida ao YouTube.
 */
@Service
public class SearchService {

    private static final Duration CACHE_TTL = Duration.ofMinutes(5);
    private static final int CACHE_SIZE = 200;

    private record Cached(List<TrackSummary> tracks, Instant at) {
    }

    private final TrackLoader trackLoader;
    private final MeterRegistry meterRegistry;
    private final Clock clock;
    private final Map<String, Cached> cache = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Cached> eldest) {
            return size() > CACHE_SIZE;
        }
    };

    @Autowired
    public SearchService(TrackLoader trackLoader, MeterRegistry meterRegistry) {
        this(trackLoader, meterRegistry, Clock.systemUTC());
    }

    SearchService(TrackLoader trackLoader, MeterRegistry meterRegistry, Clock clock) {
        this.trackLoader = trackLoader;
        this.meterRegistry = meterRegistry;
        this.clock = clock;
    }

    public List<TrackSummary> search(String query, int limit) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        var key = query.trim().toLowerCase(Locale.ROOT);
        var cached = cached(key);
        if (cached != null) {
            meterRegistry.counter("sabadaco.searches", "outcome", "cached").increment();
            return cached.stream().limit(limit).toList();
        }
        var tracks = switch (trackLoader.loadNow(new Object(), TrackLoader.SEARCH_PREFIX + query.trim())) {
            case LoadResult.PlaylistLoaded playlist -> playlist.tracks().stream().map(TrackSummary::of).toList();
            case LoadResult.TrackLoaded loaded -> List.of(TrackSummary.of(loaded.track()));
            case LoadResult.NoMatches ignored -> List.<TrackSummary>of();
            case LoadResult.Failed ignored -> List.<TrackSummary>of();
        };
        meterRegistry.counter("sabadaco.searches", "outcome", tracks.isEmpty() ? "empty" : "found").increment();
        if (!tracks.isEmpty()) {
            synchronized (cache) {
                cache.put(key, new Cached(tracks, clock.instant()));
            }
        }
        return tracks.stream().limit(limit).toList();
    }

    private List<TrackSummary> cached(String key) {
        synchronized (cache) {
            var entry = cache.get(key);
            if (entry == null) {
                return null;
            }
            if (entry.at().plus(CACHE_TTL).isBefore(clock.instant())) {
                cache.remove(key);
                return null;
            }
            return entry.tracks();
        }
    }
}
