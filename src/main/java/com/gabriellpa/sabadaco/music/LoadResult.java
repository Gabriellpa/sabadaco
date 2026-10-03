package com.gabriellpa.sabadaco.music;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import java.util.List;

public sealed interface LoadResult {

    record TrackLoaded(AudioTrack track) implements LoadResult {
    }

    record PlaylistLoaded(String name, List<AudioTrack> tracks, boolean searchResult) implements LoadResult {
    }

    record NoMatches() implements LoadResult {
    }

    record Failed(String message) implements LoadResult {
    }
}
