package com.gabriellpa.sabadaco.metrics;

import java.util.Map;

/**
 * Uma "foto" das métricas, tirada a cada intervalo. Valores {@code ...PerInterval} são deltas desde a
 * foto anterior; o resto é o valor no instante {@code t}.
 */
public record MetricsSample(
        long t,
        // Áudio
        double sentKBps,
        double downloadedKBps,
        long tracksStarted,
        long tracksFailed,
        // Comandos
        long commands,
        long commandErrors,
        Map<String, Long> commandsByName,
        double latencyMeanMs,
        double latencyP95Ms,
        double latencyP99Ms,
        // Uso
        int playing,
        int queued,
        int listeners,
        long activeUsers,
        long searches,
        long searchesCached,
        // Discord
        int voiceConnections,
        long gatewayPingMs,
        // Sistema
        double cpuProcessPct,
        double cpuSystemPct,
        double heapUsedMb,
        double heapCommittedMb,
        double nonHeapMb,
        int threads,
        long gcPauseMs
) {
}
