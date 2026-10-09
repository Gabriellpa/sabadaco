package com.gabriellpa.sabadaco.metrics;

import com.gabriellpa.sabadaco.discord.DiscordDirectory;
import com.gabriellpa.sabadaco.discord.DiscordDirectory.DiscordStats;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.ToDoubleFunction;

/**
 * Estado do Discord e uso em {@code /actuator/prometheus}, para o Grafana ter o que o painel admin mostra:
 * <ul>
 *     <li>{@code sabadaco.discord.guilds}, {@code sabadaco.discord.voice.connections}, {@code sabadaco.discord.listeners}</li>
 *     <li>{@code sabadaco.discord.gateway.ping} — latência do gateway (ms)</li>
 *     <li>{@code sabadaco.users.active} — pessoas que usaram o bot nos últimos 5 minutos</li>
 * </ul>
 * Os valores são lidos na hora da coleta; se o JDA ainda não estiver pronto, ficam em 0.
 */
@Component
public class DiscordMetrics implements MeterBinder {

    private static final Duration ACTIVE_WINDOW = Duration.ofMinutes(5);
    private static final DiscordStats EMPTY = new DiscordStats(0, 0, 0, 0);

    private final DiscordDirectory directory;
    private final ActivityTracker activity;

    public DiscordMetrics(DiscordDirectory directory, ActivityTracker activity) {
        this.directory = directory;
        this.activity = activity;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        gauge(registry, "sabadaco.discord.guilds", "Servidores onde o bot está", DiscordStats::guilds);
        gauge(registry, "sabadaco.discord.voice.connections", "Canais de voz conectados", DiscordStats::voiceConnections);
        gauge(registry, "sabadaco.discord.listeners", "Pessoas ouvindo nos canais de voz do bot", DiscordStats::listeners);
        Gauge.builder("sabadaco.discord.gateway.ping", this, metrics -> metrics.stats().gatewayPingMs())
                .description("Latência do gateway do Discord")
                .baseUnit("milliseconds")
                .register(registry);
        Gauge.builder("sabadaco.users.active", activity, tracker -> tracker.activeUsers(ACTIVE_WINDOW))
                .description("Pessoas que usaram o bot nos últimos 5 minutos")
                .register(registry);
    }

    private void gauge(MeterRegistry registry, String name, String description, ToDoubleFunction<DiscordStats> value) {
        Gauge.builder(name, this, metrics -> value.applyAsDouble(metrics.stats()))
                .description(description)
                .register(registry);
    }

    private DiscordStats stats() {
        try {
            return directory.stats();
        } catch (RuntimeException e) {
            return EMPTY;
        }
    }
}
