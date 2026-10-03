package com.gabriellpa.sabadaco.admin;

import com.gabriellpa.sabadaco.discord.DiscordDirectory;
import com.gabriellpa.sabadaco.metrics.ActivityTracker;
import com.gabriellpa.sabadaco.metrics.ActivityTracker.Ranked;
import com.gabriellpa.sabadaco.metrics.MetricsSample;
import com.gabriellpa.sabadaco.metrics.MetricsSampler;
import com.gabriellpa.sabadaco.playlist.Playlist;
import com.gabriellpa.sabadaco.playlist.PlaylistScope;
import com.gabriellpa.sabadaco.playlist.PlaylistService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;

/**
 * Aba de métricas: a página e a API JSON que ela consulta a cada 2 s
 * (histórico incremental via {@code since} + um resumo atual para rankings e totais).
 */
@Controller
@RequiredArgsConstructor
public class MetricsController {

    public record MetricsResponse(List<MetricsSample> samples, Summary summary) {
    }

    public record Summary(
            long uptimeSeconds, double cpuTimeSeconds, int cpus, double heapMaxMb,
            double sentMbTotal, double downloadedMbTotal, long tracksPlayedTotal, long tracksFailedTotal,
            long knownUsers, long activeUsersHour, long singlePlays, long playlistPlays,
            long playlists, long guildPlaylists, long globalPlaylists, long playlistTracks, long aliasedTracks,
            int guilds,
            List<Ranked> topTracks, List<Ranked> topCommands, List<Ranked> topUsers, List<Ranked> biggestPlaylists) {
    }

    private static final int TOP = 6;

    private final MetricsSampler sampler;
    private final ActivityTracker activity;
    private final PlaylistService playlistService;
    private final DiscordDirectory directory;
    private final MeterRegistry registry;

    @GetMapping("/admin/metrics")
    public String page() {
        return "admin/metrics";
    }

    @GetMapping("/admin/api/metrics")
    @ResponseBody
    public MetricsResponse metrics(@RequestParam(defaultValue = "0") long since) {
        return new MetricsResponse(sampler.history(since), summary());
    }

    private Summary summary() {
        var runtime = ManagementFactory.getRuntimeMXBean();
        var os = (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        var playlists = playlistService.search(null, null);
        int guilds;
        try {
            guilds = directory.guilds().size();
        } catch (RuntimeException e) {
            guilds = 0;
        }
        return new Summary(
                runtime.getUptime() / 1000,
                os.getProcessCpuTime() / 1e9,
                os.getAvailableProcessors(),
                ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getMax() / (1024.0 * 1024),
                registry.find("sabadaco.audio.sent.bytes").functionCounters().stream().mapToDouble(FunctionCounter::count).sum() / (1024 * 1024),
                counterSum("sabadaco.audio.downloaded.bytes") / (1024 * 1024),
                (long) counterSum("sabadaco.tracks.played"),
                (long) counterSum("sabadaco.tracks.failed"),
                activity.knownUsers(),
                activity.activeUsers(Duration.ofHours(1)),
                activity.singlePlays(),
                activity.playlistPlays(),
                playlists.size(),
                playlists.stream().filter(p -> p.scope() == PlaylistScope.GUILD).count(),
                playlists.stream().filter(p -> p.scope() == PlaylistScope.GLOBAL).count(),
                playlists.stream().mapToLong(p -> p.tracks().size()).sum(),
                playlists.stream().flatMap(p -> p.tracks().stream()).filter(t -> t.alias() != null).count(),
                guilds,
                activity.topTracks(TOP),
                activity.topCommands(TOP),
                activity.topUsers(directory::userName, TOP),
                playlists.stream()
                        .sorted(Comparator.comparingInt((Playlist p) -> p.tracks().size()).reversed())
                        .limit(TOP)
                        .map(p -> new Ranked(p.name() + " · " + directory.userName(p.ownerId()), p.tracks().size()))
                        .toList());
    }

    private double counterSum(String name) {
        return registry.find(name).counters().stream().mapToDouble(Counter::count).sum();
    }
}
