package com.gabriellpa.sabadaco.music;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

/**
 * Dados imutáveis de uma faixa, sem dependência do Lavaplayer para quem consome (UI, admin, playlists).
 */
public record TrackSummary(String title, String author, String uri, long durationMs, String artworkUrl, boolean stream) {

    public static TrackSummary of(AudioTrack track) {
        var info = track.getInfo();
        var artwork = info.artworkUrl;
        if (artwork == null && track.getSourceManager() != null && "youtube".equals(track.getSourceManager().getSourceName())) {
            // O youtube-source v2 não preenche artworkUrl; a thumbnail é derivada do id do vídeo
            artwork = "https://i.ytimg.com/vi/" + info.identifier + "/hqdefault.jpg";
        }
        return new TrackSummary(info.title, info.author, info.uri, info.length, artwork, info.isStream);
    }
}
