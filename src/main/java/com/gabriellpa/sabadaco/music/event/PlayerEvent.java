package com.gabriellpa.sabadaco.music.event;

import com.gabriellpa.sabadaco.music.QueuedTrack;

/**
 * Eventos do player publicados como Spring events. Quem quiser reagir (painel do Discord, métricas, ...)
 * só precisa de um {@code @EventListener}, sem acoplar ao núcleo de música.
 */
public sealed interface PlayerEvent {

    long guildId();

    record TrackStarted(long guildId, QueuedTrack track) implements PlayerEvent {
    }

    record TrackEnded(long guildId, QueuedTrack track, String reason, long bytesSent, long listenedMs) implements PlayerEvent {
    }

    record TrackFailed(long guildId, QueuedTrack track, String message) implements PlayerEvent {
    }

    /** Pausa, volume, loop ou fila mudaram. */
    record StateChanged(long guildId) implements PlayerEvent {
    }

    record Stopped(long guildId) implements PlayerEvent {
    }
}
