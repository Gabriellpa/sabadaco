package com.gabriellpa.sabadaco.metrics;

import com.gabriellpa.sabadaco.discord.DiscordDirectory;
import com.gabriellpa.sabadaco.discord.DiscordDirectory.DiscordStats;
import com.gabriellpa.sabadaco.music.MusicService;
import com.gabriellpa.sabadaco.music.PlayerSnapshot;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.ValueAtPercentile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Tira uma foto das métricas a cada {@value #INTERVAL_MS} ms e guarda os últimos 15 minutos em memória,
 * para os gráficos do painel abrirem já preenchidos e atualizarem em tempo real.
 * Contadores do Micrometer são acumulados; aqui viram taxas (delta entre duas fotos).
 */
@Slf4j
@Component
public class MetricsSampler {

    static final long INTERVAL_MS = 2_000;
    private static final int MAX_SAMPLES = 450; // 15 min
    private static final double MB = 1024.0 * 1024;

    private final MeterRegistry registry;
    private final ActivityTracker activity;
    private final MusicService musicService;
    private final DiscordDirectory directory;
    private final Deque<MetricsSample> history = new ArrayDeque<>();
    private final com.sun.management.OperatingSystemMXBean os =
            (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();

    private Totals previous;

    /** Valores acumulados de uma foto, para calcular deltas na próxima. */
    private record Totals(long t, double sent, double downloaded, double tracks, double failed, long commands,
                          long errors, Map<String, Long> byName, long latencyCount, double latencyTotalMs,
                          double searches, double cached, long gcMs) {
    }

    public MetricsSampler(MeterRegistry registry, ActivityTracker activity, MusicService musicService, DiscordDirectory directory) {
        this.registry = registry;
        this.activity = activity;
        this.musicService = musicService;
        this.directory = directory;
    }

    @Scheduled(fixedRate = INTERVAL_MS)
    public void sample() {
        var now = totals();
        if (previous != null) {
            var sample = toSample(previous, now);
            synchronized (history) {
                history.addLast(sample);
                while (history.size() > MAX_SAMPLES) {
                    history.removeFirst();
                }
            }
        }
        previous = now;
    }

    /** Fotos com {@code t > since} (0 = todas). */
    public List<MetricsSample> history(long since) {
        synchronized (history) {
            return history.stream().filter(sample -> sample.t() > since).toList();
        }
    }

    private MetricsSample toSample(Totals before, Totals now) {
        double seconds = Math.max(0.001, (now.t() - before.t()) / 1000.0);
        long latencyCount = now.latencyCount() - before.latencyCount();
        double latencyMean = latencyCount > 0 ? (now.latencyTotalMs() - before.latencyTotalMs()) / latencyCount : 0;
        var percentiles = activity.latency().takeSnapshot().percentileValues();
        var players = musicService.snapshots();
        var discord = discordStats();
        var memory = ManagementFactory.getMemoryMXBean();

        return new MetricsSample(
                now.t(),
                (now.sent() - before.sent()) / 1024 / seconds,
                (now.downloaded() - before.downloaded()) / 1024 / seconds,
                (long) (now.tracks() - before.tracks()),
                (long) (now.failed() - before.failed()),
                now.commands() - before.commands(),
                now.errors() - before.errors(),
                delta(before.byName(), now.byName()),
                latencyMean,
                percentile(percentiles, 0.95),
                percentile(percentiles, 0.99),
                (int) players.stream().filter(PlayerSnapshot::playing).count(),
                players.stream().mapToInt(PlayerSnapshot::queueSize).sum(),
                discord.listeners(),
                activity.activeUsers(Duration.ofMinutes(5)),
                (long) (now.searches() - before.searches()),
                (long) (now.cached() - before.cached()),
                discord.voiceConnections(),
                discord.gatewayPingMs(),
                Math.max(0, os.getProcessCpuLoad()) * 100,
                Math.max(0, os.getCpuLoad()) * 100,
                memory.getHeapMemoryUsage().getUsed() / MB,
                memory.getHeapMemoryUsage().getCommitted() / MB,
                memory.getNonHeapMemoryUsage().getUsed() / MB,
                ManagementFactory.getThreadMXBean().getThreadCount(),
                now.gcMs() - before.gcMs());
    }

    private Totals totals() {
        var byName = new HashMap<String, Long>();
        long commands = 0;
        long errors = 0;
        for (var metric : List.of("sabadaco.commands", "sabadaco.components")) {
            for (Timer timer : registry.find(metric).timers()) {
                var name = timer.getId().getTag(metric.endsWith("commands") ? "command" : "component");
                byName.merge(name, timer.count(), Long::sum);
                commands += timer.count();
                if (!"success".equals(timer.getId().getTag("outcome"))) {
                    errors += timer.count();
                }
            }
        }
        var latency = activity.latency();
        return new Totals(
                System.currentTimeMillis(),
                registry.find("sabadaco.audio.sent.bytes").functionCounters().stream().mapToDouble(FunctionCounter::count).sum(),
                counterSum("sabadaco.audio.downloaded.bytes", null),
                counterSum("sabadaco.tracks.played", null),
                counterSum("sabadaco.tracks.failed", null),
                commands,
                errors,
                byName,
                latency.count(),
                latency.totalTime(TimeUnit.MILLISECONDS),
                counterSum("sabadaco.searches", null),
                counterSum("sabadaco.searches", "cached"),
                ManagementFactory.getGarbageCollectorMXBeans().stream()
                        .mapToLong(GarbageCollectorMXBean::getCollectionTime).filter(ms -> ms > 0).sum());
    }

    private double counterSum(String name, String outcome) {
        var search = registry.find(name);
        if (outcome != null) {
            search = search.tag("outcome", outcome);
        }
        return search.counters().stream().mapToDouble(Counter::count).sum();
    }

    private DiscordStats discordStats() {
        try {
            return directory.stats();
        } catch (RuntimeException e) {
            log.debug("Discord ainda não disponível para métricas: {}", e.getMessage());
            return new DiscordStats(0, 0, 0, 0);
        }
    }

    private static Map<String, Long> delta(Map<String, Long> before, Map<String, Long> now) {
        var result = new HashMap<String, Long>();
        now.forEach((name, count) -> {
            long diff = count - before.getOrDefault(name, 0L);
            if (diff > 0) {
                result.put(name, diff);
            }
        });
        return result;
    }

    private static double percentile(ValueAtPercentile[] values, double which) {
        for (var value : values) {
            if (value.percentile() == which) {
                double ms = value.value(TimeUnit.MILLISECONDS);
                return Double.isNaN(ms) ? 0 : ms;
            }
        }
        return 0;
    }
}
