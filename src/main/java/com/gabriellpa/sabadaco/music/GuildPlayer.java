package com.gabriellpa.sabadaco.music;

import com.gabriellpa.sabadaco.music.event.PlayerEvent;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame;
import lombok.AccessLevel;
import lombok.Getter;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Player de um servidor: o {@link AudioPlayer} do Lavaplayer mais a fila ({@link TrackScheduler}).
 */
@Getter
public class GuildPlayer {

    private final long guildId;
    private final AudioPlayer audioPlayer;
    private final TrackScheduler scheduler;
    @Getter(AccessLevel.NONE)
    private final List<Consumer<byte[]>> taps = new CopyOnWriteArrayList<>();

    public GuildPlayer(long guildId, AudioPlayerManager manager, Consumer<PlayerEvent> events) {
        this.guildId = guildId;
        this.audioPlayer = manager.createPlayer();
        this.scheduler = new TrackScheduler(guildId, audioPlayer, events);
        this.audioPlayer.addListener(scheduler);
    }

    /** Próximo frame Opus para o Discord (ou {@code null}); também contabiliza os bytes enviados. */
    public AudioFrame provideFrame() {
        var frame = audioPlayer.provide();
        if (frame != null) {
            scheduler.recordSentBytes(frame.getDataLength());
            if (!taps.isEmpty()) {
                var data = frame.getData();
                taps.forEach(tap -> tap.accept(data));
            }
        }
        return frame;
    }

    /**
     * Recebe uma cópia de cada frame Opus enviado ao Discord (usado para ouvir pelo painel admin).
     * O consumidor roda na thread de áudio: precisa ser instantâneo (ex.: {@code queue::offer}).
     */
    public AutoCloseable tap(Consumer<byte[]> listener) {
        taps.add(listener);
        return () -> taps.remove(listener);
    }

    public PlayerSnapshot snapshot() {
        return scheduler.snapshot();
    }
}
