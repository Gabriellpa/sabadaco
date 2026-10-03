package com.gabriellpa.sabadaco.metrics;

import com.gabriellpa.sabadaco.discord.interaction.InteractionExecuted;
import com.gabriellpa.sabadaco.music.TrackSummary;
import com.gabriellpa.sabadaco.music.event.PlayerEvent;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * Estatísticas de uso para o painel admin: quem usa, o que toca, quais comandos e com que latência.
 * Alimentado por eventos ({@link InteractionExecuted}, {@link PlayerEvent}); nada aqui bloqueia quem publica.
 */
@Component
public class ActivityTracker {

    /** Item de ranking (top músicas, usuários, comandos). */
    public record Ranked(String label, long value) {
    }

    /** Para não crescer sem limite num bot que roda meses. */
    private static final int MAX_TRACKED_TRACKS = 2_000;

    private final Clock clock;
    private final Timer latency;
    private final Map<Long, AtomicLong> commandsByUser = new ConcurrentHashMap<>();
    private final Map<Long, Instant> lastSeen = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> commandTotals = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> playsByTitle = new ConcurrentHashMap<>();
    private final AtomicLong singlePlays = new AtomicLong();
    private final AtomicLong playlistPlays = new AtomicLong();

    @Autowired
    public ActivityTracker(MeterRegistry registry) {
        this(registry, Clock.systemUTC());
    }

    ActivityTracker(MeterRegistry registry, Clock clock) {
        this.clock = clock;
        // Uma latência única (sem tags) para calcular p95/p99 do bot como um todo, numa janela móvel de ~2 min
        this.latency = Timer.builder("sabadaco.interactions.latency")
                .description("Tempo de resposta de comandos e botões")
                .publishPercentiles(0.5, 0.95, 0.99)
                .distributionStatisticExpiry(Duration.ofMinutes(2))
                .register(registry);
    }

    @EventListener
    public void onInteraction(InteractionExecuted event) {
        latency.record(event.durationNanos(), TimeUnit.NANOSECONDS);
        commandTotals.computeIfAbsent(event.name(), name -> new AtomicLong()).incrementAndGet();
        if (event.userId() != 0) {
            commandsByUser.computeIfAbsent(event.userId(), id -> new AtomicLong()).incrementAndGet();
            lastSeen.put(event.userId(), clock.instant());
        }
    }

    @EventListener
    public void onTrackStarted(PlayerEvent.TrackStarted event) {
        (event.track().fromPlaylist() ? playlistPlays : singlePlays).incrementAndGet();
        var title = TrackSummary.of(event.track().track()).title();
        if (playsByTitle.size() < MAX_TRACKED_TRACKS || playsByTitle.containsKey(title)) {
            playsByTitle.computeIfAbsent(title, key -> new AtomicLong()).incrementAndGet();
        }
        if (event.track().requesterId() != 0) {
            lastSeen.put(event.track().requesterId(), clock.instant());
        }
    }

    public Timer latency() {
        return latency;
    }

    public long activeUsers(Duration window) {
        var since = clock.instant().minus(window);
        return lastSeen.values().stream().filter(seen -> seen.isAfter(since)).count();
    }

    public long knownUsers() {
        return lastSeen.size();
    }

    public long singlePlays() {
        return singlePlays.get();
    }

    public long playlistPlays() {
        return playlistPlays.get();
    }

    public List<Ranked> topTracks(int limit) {
        return top(playsByTitle, Function.identity(), limit);
    }

    public List<Ranked> topCommands(int limit) {
        return top(commandTotals, name -> name.contains(":") ? "🔘 " + name : "/" + name, limit);
    }

    public List<Ranked> topUsers(Function<Long, String> names, int limit) {
        return top(commandsByUser, names, limit);
    }

    private static <K> List<Ranked> top(Map<K, AtomicLong> counts, Function<K, String> label, int limit) {
        return counts.entrySet().stream()
                .map(entry -> new Ranked(label.apply(entry.getKey()), entry.getValue().get()))
                .sorted(Comparator.comparingLong(Ranked::value).reversed())
                .limit(limit)
                .toList();
    }
}
