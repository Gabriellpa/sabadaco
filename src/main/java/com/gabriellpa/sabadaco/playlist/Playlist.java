package com.gabriellpa.sabadaco.playlist;

import java.util.List;

/**
 * Playlist sempre pertence a um usuário. Escopo {@link PlaylistScope#GUILD} vale só no servidor
 * em que foi criada; {@link PlaylistScope#GLOBAL} vale em qualquer servidor.
 *
 * @param guildId servidor da playlist ({@code null} quando global)
 */
public record Playlist(String id, long ownerId, PlaylistScope scope, Long guildId, String name, List<PlaylistTrack> tracks) {

    public Playlist {
        tracks = List.copyOf(tracks);
    }

    public Playlist withName(String newName) {
        return new Playlist(id, ownerId, scope, guildId, newName, tracks);
    }

    public Playlist withTracks(List<PlaylistTrack> newTracks) {
        return new Playlist(id, ownerId, scope, guildId, name, newTracks);
    }

    public boolean visibleIn(long guild) {
        return scope == PlaylistScope.GLOBAL || guildId == guild;
    }

    /** Nome com indicação de escopo, para listas e autocomplete. */
    public String label() {
        return scope == PlaylistScope.GLOBAL ? name + " (global)" : name;
    }

    public long totalDurationMs() {
        return tracks.stream().mapToLong(PlaylistTrack::durationMs).sum();
    }
}
