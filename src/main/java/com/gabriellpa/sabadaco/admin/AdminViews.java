package com.gabriellpa.sabadaco.admin;

import com.gabriellpa.sabadaco.discord.DiscordDirectory;
import com.gabriellpa.sabadaco.discord.ui.Format;
import com.gabriellpa.sabadaco.music.LoopMode;
import com.gabriellpa.sabadaco.music.MusicService;
import com.gabriellpa.sabadaco.music.PlayerSnapshot;
import com.gabriellpa.sabadaco.discord.ui.PlayerPanel;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Monta os dados das telas do admin e expõe helpers de formatação para os templates ({@code @views}).
 */
@Component("views")
@RequiredArgsConstructor
public class AdminViews {

    public record GuildCard(long id, String name, PlayerSnapshot player) {
        public boolean playing() {
            return player != null && player.playing();
        }
    }

    public record Stats(String downloaded, String sent, long tracksPlayed, long commands, long searches) {
    }

    private final MusicService musicService;
    private final DiscordDirectory directory;
    private final MeterRegistry meterRegistry;

    /** Todos os servidores do bot, os que estão tocando primeiro. */
    public List<GuildCard> guildCards() {
        return directory.guilds().stream()
                .map(guild -> new GuildCard(guild.id(), guild.name(), musicService.snapshot(guild.id()).orElse(null)))
                .sorted((a, b) -> Boolean.compare(b.playing(), a.playing()))
                .toList();
    }

    public GuildCard guildCard(long guildId) {
        return new GuildCard(guildId, directory.guildName(guildId), musicService.snapshot(guildId).orElse(null));
    }

    public Stats stats() {
        return new Stats(
                Format.bytes((long) counterSum("sabadaco.audio.downloaded.bytes")),
                Format.bytes((long) meterRegistry.find("sabadaco.audio.sent.bytes").functionCounters().stream().mapToDouble(FunctionCounter::count).sum()),
                (long) counterSum("sabadaco.tracks.played"),
                meterRegistry.find("sabadaco.commands").timers().stream().mapToLong(Timer::count).sum(),
                (long) counterSum("sabadaco.searches"));
    }

    // Helpers usados nos templates

    public String duration(long millis) {
        return Format.duration(millis);
    }

    public String bytes(long bytes) {
        return Format.bytes(bytes);
    }

    public int percent(long position, long length) {
        return length <= 0 ? 0 : (int) Math.min(100, position * 100 / length);
    }

    public String user(long userId) {
        return userId == 0 ? "painel admin" : directory.userName(userId);
    }

    public String guild(Long guildId) {
        return guildId == null ? "—" : directory.guildName(guildId);
    }

    public String loop(LoopMode mode) {
        return PlayerPanel.loopLabel(mode);
    }

    private double counterSum(String name) {
        return meterRegistry.find(name).counters().stream().mapToDouble(Counter::count).sum();
    }
}
