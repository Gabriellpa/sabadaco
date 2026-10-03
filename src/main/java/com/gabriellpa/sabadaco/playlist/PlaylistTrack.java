package com.gabriellpa.sabadaco.playlist;

import com.gabriellpa.sabadaco.music.TrackSummary;

/**
 * Faixa salva. Guarda só dados simples (sem objetos do Lavaplayer) para ser fácil de persistir.
 *
 * @param alias apelido dado pelo usuário para achar a música mais fácil ({@code null} se não tiver)
 */
public record PlaylistTrack(String uri, String title, String author, long durationMs, String alias) {

    public static PlaylistTrack of(TrackSummary track, String alias) {
        return new PlaylistTrack(track.uri(), track.title(), track.author(), track.durationMs(), blankToNull(alias));
    }

    public PlaylistTrack withAlias(String newAlias) {
        return new PlaylistTrack(uri, title, author, durationMs, blankToNull(newAlias));
    }

    public String displayName() {
        return alias == null ? title : alias + " — " + title;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
