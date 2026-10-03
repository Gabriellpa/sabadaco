package com.gabriellpa.sabadaco.metrics;

import com.gabriellpa.sabadaco.music.GuildPlayer;
import com.gabriellpa.sabadaco.music.GuildPlayerRegistry;
import com.gabriellpa.sabadaco.music.QueuedTrack;
import com.gabriellpa.sabadaco.music.event.PlayerEvent;
import com.gabriellpa.sabadaco.playlist.PlaylistService;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Métricas de música derivadas dos {@link PlayerEvent}s. Expostas em {@code /actuator/prometheus}.
 * <ul>
 *     <li>{@code sabadaco.audio.downloaded.bytes} — bytes baixados das fontes (ver {@link CountingHttpEntity})</li>
 *     <li>{@code sabadaco.audio.sent.bytes} — bytes Opus enviados ao Discord, por servidor</li>
 *     <li>{@code sabadaco.track.bytes} — bytes transmitidos por música (KB da música)</li>
 *     <li>{@code sabadaco.track.listened} — tempo ouvido por música, com o motivo do fim</li>
 *     <li>{@code sabadaco.tracks.played} / {@code sabadaco.tracks.failed}</li>
 *     <li>{@code sabadaco.players.active}, {@code sabadaco.queue.size}, {@code sabadaco.playlists.saved}</li>
 *     <li>{@code sabadaco.commands}, {@code sabadaco.components}, {@code sabadaco.searches} (registradas onde acontecem)</li>
 * </ul>
 */
@Component
public class MusicMetrics {

    private final MeterRegistry registry;
    private final GuildPlayerRegistry players;

    public MusicMetrics(MeterRegistry registry, GuildPlayerRegistry players, PlaylistService playlists) {
        this.registry = registry;
        this.players = players;
        Gauge.builder("sabadaco.players.active", players, all -> all.all().stream().filter(p -> p.snapshot().playing()).count())
                .description("Servidores tocando música agora")
                .register(registry);
        Gauge.builder("sabadaco.playlists.saved", playlists, PlaylistService::count)
                .description("Playlists salvas")
                .register(registry);
    }

    @EventListener
    public void onPlayerEvent(PlayerEvent event) {
        switch (event) {
            case PlayerEvent.TrackStarted started -> {
                players.find(started.guildId()).ifPresent(this::registerGuildMeters);
                registry.counter("sabadaco.tracks.played", "source", source(started.track()), "origin", origin(started.track())).increment();
            }
            case PlayerEvent.TrackEnded ended -> {
                if (ended.bytesSent() > 0) {
                    DistributionSummary.builder("sabadaco.track.bytes")
                            .description("Bytes de áudio transmitidos por música")
                            .baseUnit("bytes")
                            .tag("source", source(ended.track()))
                            .register(registry)
                            .record(ended.bytesSent());
                }
                registry.timer("sabadaco.track.listened", "reason", ended.reason()).record(Duration.ofMillis(ended.listenedMs()));
            }
            case PlayerEvent.TrackFailed failed -> registry.counter("sabadaco.tracks.failed", "source", source(failed.track())).increment();
            case PlayerEvent.StateChanged ignored -> { }
            case PlayerEvent.Stopped ignored -> { }
        }
    }

    /** Registro idempotente: o Micrometer devolve o medidor existente se já houver. */
    private void registerGuildMeters(GuildPlayer player) {
        var guild = String.valueOf(player.getGuildId());
        FunctionCounter.builder("sabadaco.audio.sent.bytes", player.getScheduler(), scheduler -> scheduler.totalBytesSent())
                .description("Bytes de áudio Opus enviados ao Discord")
                .baseUnit("bytes")
                .tag("guild", guild)
                .register(registry);
        Gauge.builder("sabadaco.queue.size", player, p -> p.snapshot().queueSize())
                .description("Músicas na fila")
                .tag("guild", guild)
                .register(registry);
    }

    private static String source(QueuedTrack track) {
        var manager = track.track().getSourceManager();
        return manager == null ? "unknown" : manager.getSourceName();
    }

    private static String origin(QueuedTrack track) {
        return track.fromPlaylist() ? "playlist" : "single";
    }
}
