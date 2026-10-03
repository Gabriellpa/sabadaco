package com.gabriellpa.sabadaco.discord.ui;

import com.gabriellpa.sabadaco.music.EnqueueResult;

/**
 * Texto de confirmação ao enfileirar, incluindo o aviso da regra de prioridade
 * (músicas avulsas tocam antes do restante de uma playlist).
 */
public final class EnqueueMessages {

    private EnqueueMessages() {
    }

    public static String describe(EnqueueResult result, long requesterId) {
        var who = Format.mention(requesterId);
        if (result.playlistName() != null) {
            var text = new StringBuilder("📜 %s adicionou a playlist **%s** (%d música(s))".formatted(
                    who, Format.escape(result.playlistName()), result.added().size()));
            if (result.failed() > 0) {
                text.append(" — %d não puderam ser carregadas".formatted(result.failed()));
            }
            if (result.startedNow()) {
                text.append("\n▶️ Tocando agora: ").append(Format.link(result.first(), 80));
            }
            if (result.singlesAhead() > 0) {
                text.append("\n⚠️ Músicas avulsas têm prioridade: as %d que já estão na fila tocam antes da playlist."
                        .formatted(result.singlesAhead()));
            }
            return text.toString();
        }

        var track = Format.link(result.first(), 80);
        var text = new StringBuilder(result.startedNow()
                ? "▶️ %s colocou para tocar: %s".formatted(who, track)
                : "➕ %s adicionou %s (posição #%d)".formatted(who, track, result.position()));
        if (result.skippedPlaylistTracks() > 0) {
            text.append("\n⚠️ Músicas avulsas têm prioridade: esta toca antes das %d música(s) restantes da playlist **%s**."
                    .formatted(result.skippedPlaylistTracks(), Format.escape(result.skippedPlaylistName())));
        }
        return text.toString();
    }
}
