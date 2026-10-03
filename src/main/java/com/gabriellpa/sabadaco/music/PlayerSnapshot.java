package com.gabriellpa.sabadaco.music;

import java.util.List;
import java.util.stream.Stream;

/**
 * Foto imutável do estado de um player, usada pela UI do Discord e pelo painel admin.
 * A fila é dividida em duas: músicas avulsas (prioridade) e faixas de playlist.
 */
public record PlayerSnapshot(
        long guildId,
        QueueEntry current,
        long positionMs,
        boolean paused,
        int volume,
        LoopMode loopMode,
        List<QueueEntry> priorityQueue,
        List<QueueEntry> playlistQueue,
        long currentTrackBytes
) {

    public boolean playing() {
        return current != null;
    }

    public int queueSize() {
        return priorityQueue.size() + playlistQueue.size();
    }

    /** Fila na ordem em que vai tocar. */
    public List<QueueEntry> upcoming() {
        return Stream.concat(priorityQueue.stream(), playlistQueue.stream()).toList();
    }
}
