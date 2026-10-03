package com.gabriellpa.sabadaco.music;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

/**
 * Faixa na fila com quem pediu e, se veio de uma playlist, o nome dela.
 */
public record QueuedTrack(AudioTrack track, long requesterId, String playlistName) {

    public static QueuedTrack single(AudioTrack track, long requesterId) {
        return new QueuedTrack(track, requesterId, null);
    }

    public static QueuedTrack fromPlaylist(AudioTrack track, long requesterId, String playlistName) {
        return new QueuedTrack(track, requesterId, playlistName);
    }

    public boolean fromPlaylist() {
        return playlistName != null;
    }

    public QueueEntry toEntry() {
        return new QueueEntry(TrackSummary.of(track), requesterId, playlistName);
    }

    QueuedTrack copy() {
        return new QueuedTrack(track.makeClone(), requesterId, playlistName);
    }
}
