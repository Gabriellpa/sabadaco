package com.gabriellpa.sabadaco.discord.ui;

import com.gabriellpa.sabadaco.music.MusicService;
import com.gabriellpa.sabadaco.music.event.PlayerEvent;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Mantém uma mensagem de painel por servidor e a atualiza a cada {@link PlayerEvent}
 * (inclusive quando a ação vem do painel admin). Rajadas de eventos viram uma única edição.
 */
@Slf4j
@Component
public class PlayerPanelUpdater {

    private record PanelRef(long channelId, long messageId) {
    }

    private static final long DEBOUNCE_MS = 750;

    private final JDA jda;
    private final MusicService musicService;
    private final Map<Long, PanelRef> panels = new ConcurrentHashMap<>();
    private final Set<Long> pendingRefresh = ConcurrentHashMap.newKeySet();
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().name("panel-updater").factory());

    public PlayerPanelUpdater(@Lazy JDA jda, MusicService musicService) {
        this.jda = jda;
        this.musicService = musicService;
    }

    public boolean hasPanel(long guildId) {
        return panels.containsKey(guildId);
    }

    /** Envia um novo painel no canal (apagando o anterior, se houver). */
    public void publish(long guildId, MessageChannel channel) {
        var snapshot = musicService.snapshot(guildId).orElse(null);
        channel.sendMessage(Messages.create(PlayerPanel.render(snapshot))).queue(message -> {
            var previous = panels.put(guildId, new PanelRef(channel.getIdLong(), message.getIdLong()));
            if (previous != null) {
                channel(previous).ifPresent(old -> old.deleteMessageById(previous.messageId()).queue(null, ignored -> { }));
            }
        }, error -> log.warn("Sem permissão para enviar o painel no canal {}: {}", channel.getName(), error.getMessage()));
    }

    @EventListener
    public void onPlayerEvent(PlayerEvent event) {
        long guildId = event.guildId();
        if (hasPanel(guildId) && pendingRefresh.add(guildId)) {
            scheduler.schedule(() -> {
                pendingRefresh.remove(guildId);
                refresh(guildId);
            }, DEBOUNCE_MS, TimeUnit.MILLISECONDS);
        }
    }

    /** Atualiza a barra de progresso de quem está tocando. */
    @Scheduled(fixedDelay = 15_000)
    public void refreshProgress() {
        panels.keySet().forEach(guildId -> musicService.snapshot(guildId)
                .filter(snapshot -> snapshot.playing() && !snapshot.paused())
                .ifPresent(ignored -> refresh(guildId)));
    }

    private void refresh(long guildId) {
        var ref = panels.get(guildId);
        if (ref == null) {
            return;
        }
        var snapshot = musicService.snapshot(guildId).orElse(null);
        channel(ref).ifPresentOrElse(
                channel -> channel.editMessageById(ref.messageId(), Messages.edit(PlayerPanel.render(snapshot)))
                        .queue(null, error -> panels.remove(guildId, ref)),
                () -> panels.remove(guildId, ref));
    }

    private Optional<GuildMessageChannel> channel(PanelRef ref) {
        return Optional.ofNullable(jda.getChannelById(GuildMessageChannel.class, ref.channelId()));
    }
}
