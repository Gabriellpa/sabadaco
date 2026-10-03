package com.gabriellpa.sabadaco.music;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SearchService {

    private final TrackLoader trackLoader;
    private final MeterRegistry meterRegistry;

    public List<TrackSummary> search(String query, int limit) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        var tracks = switch (trackLoader.loadNow(new Object(), TrackLoader.SEARCH_PREFIX + query.trim())) {
            case LoadResult.PlaylistLoaded playlist -> playlist.tracks().stream().limit(limit).map(TrackSummary::of).toList();
            case LoadResult.TrackLoaded loaded -> List.of(TrackSummary.of(loaded.track()));
            case LoadResult.NoMatches ignored -> List.<TrackSummary>of();
            case LoadResult.Failed ignored -> List.<TrackSummary>of();
        };
        meterRegistry.counter("sabadaco.searches", "outcome", tracks.isEmpty() ? "empty" : "found").increment();
        return tracks;
    }
}
