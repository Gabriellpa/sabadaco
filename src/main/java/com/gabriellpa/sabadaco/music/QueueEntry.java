package com.gabriellpa.sabadaco.music;

public record QueueEntry(TrackSummary track, long requesterId, String playlistName) {

    public boolean fromPlaylist() {
        return playlistName != null;
    }
}
