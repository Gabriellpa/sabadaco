package com.gabriellpa.sabadaco.music;

import com.gabriellpa.sabadaco.UserFacingException;
import com.gabriellpa.sabadaco.music.event.PlayerEvent;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Fila de um servidor com duas "pistas":
 * <ul>
 *     <li><b>priority</b>: músicas avulsas ({@code /play}, busca), que sempre tocam primeiro;</li>
 *     <li><b>playlist</b>: faixas de playlists, que tocam quando não há avulsas.</li>
 * </ul>
 */
@Slf4j
public class TrackScheduler extends AudioEventAdapter {

    private final long guildId;
    private final AudioPlayer player;
    private final Consumer<PlayerEvent> events;
    private final List<QueuedTrack> priorityLane = new ArrayList<>();
    private final List<QueuedTrack> playlistLane = new ArrayList<>();
    private final AtomicLong currentTrackBytes = new AtomicLong();
    private final AtomicLong totalBytesSent = new AtomicLong();
    private QueuedTrack current;
    @Getter
    private LoopMode loopMode = LoopMode.OFF;

    public TrackScheduler(long guildId, AudioPlayer player, Consumer<PlayerEvent> events) {
        this.guildId = guildId;
        this.player = player;
        this.events = events;
    }

    public synchronized EnqueueResult enqueue(List<QueuedTrack> tracks) {
        if (tracks.isEmpty()) {
            throw new UserFacingException("Nenhuma faixa para adicionar.");
        }
        var first = tracks.getFirst();
        int position = current == null ? 0 : positionFor(first);
        int skippedPlaylistTracks = !first.fromPlaylist() && position > 0 ? playlistLane.size() : 0;
        String skippedPlaylistName = skippedPlaylistTracks > 0 ? playlistLane.getFirst().playlistName() : null;
        int singlesAhead = first.fromPlaylist() ? priorityLane.size() : 0;

        for (QueuedTrack track : tracks) {
            if (current == null) {
                start(track);
            } else {
                laneOf(track).add(track);
            }
        }
        events.accept(new PlayerEvent.StateChanged(guildId));
        return new EnqueueResult(tracks.stream().map(t -> TrackSummary.of(t.track())).toList(),
                first.playlistName(), position, skippedPlaylistTracks, skippedPlaylistName, singlesAhead, 0);
    }

    /** Avança para a próxima faixa respeitando o loop (fim natural da faixa). */
    public synchronized void next() {
        startOrIdle(pollNext(false));
    }

    /** Pula a faixa atual, mesmo com loop de faixa ligado. */
    public synchronized void skip() {
        startOrIdle(pollNext(true));
    }

    public synchronized void stop() {
        priorityLane.clear();
        playlistLane.clear();
        loopMode = LoopMode.OFF;
        current = null;
        player.stopTrack();
        player.setPaused(false);
        events.accept(new PlayerEvent.Stopped(guildId));
    }

    /** Avisa interessados (painel, admin) que pausa/volume mudaram. */
    public void onStateChanged() {
        events.accept(new PlayerEvent.StateChanged(guildId));
    }

    public synchronized LoopMode cycleLoop() {
        loopMode = loopMode.next();
        events.accept(new PlayerEvent.StateChanged(guildId));
        return loopMode;
    }

    public synchronized void shuffle() {
        Collections.shuffle(priorityLane);
        Collections.shuffle(playlistLane);
        events.accept(new PlayerEvent.StateChanged(guildId));
    }

    public synchronized void clear() {
        priorityLane.clear();
        playlistLane.clear();
        events.accept(new PlayerEvent.StateChanged(guildId));
    }

    /** Remove pelo índice na ordem de execução (avulsas primeiro, depois playlist). */
    public synchronized QueueEntry remove(int index) {
        var lane = laneAt(index);
        var removed = lane.remove(indexInLane(index));
        events.accept(new PlayerEvent.StateChanged(guildId));
        return removed.toEntry();
    }

    /** Move dentro da mesma pista; mover entre pistas quebraria a regra de prioridade. */
    public synchronized void move(int from, int to) {
        var lane = laneAt(from);
        if (lane != laneAt(to)) {
            throw new UserFacingException("Só é possível mover dentro do mesmo grupo (avulsas ou playlist).");
        }
        int target = indexInLane(to);
        lane.add(target, lane.remove(indexInLane(from)));
        events.accept(new PlayerEvent.StateChanged(guildId));
    }

    public synchronized PlayerSnapshot snapshot() {
        var playing = player.getPlayingTrack();
        return new PlayerSnapshot(
                guildId,
                current == null ? null : current.toEntry(),
                playing == null ? 0 : playing.getPosition(),
                player.isPaused(),
                player.getVolume(),
                loopMode,
                priorityLane.stream().map(QueuedTrack::toEntry).toList(),
                playlistLane.stream().map(QueuedTrack::toEntry).toList(),
                currentTrackBytes.get());
    }

    /** Chamado pela thread de áudio a cada frame enviado ao Discord (~50x por segundo). */
    public void recordSentBytes(int bytes) {
        currentTrackBytes.addAndGet(bytes);
        totalBytesSent.addAndGet(bytes);
    }

    public long totalBytesSent() {
        return totalBytesSent.get();
    }

    @Override
    public void onTrackStart(AudioPlayer player, AudioTrack track) {
        events.accept(new PlayerEvent.TrackStarted(guildId, queued(track)));
    }

    /**
     * O Lavaplayer chama este listener segurando o lock interno dele; avançar aqui (que pega o lock
     * do scheduler) poderia dar deadlock com um {@link #skip()} concorrente. Por isso o avanço é assíncrono.
     */
    @Override
    public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason) {
        events.accept(new PlayerEvent.TrackEnded(guildId, queued(track), endReason.name(),
                currentTrackBytes.getAndSet(0), track.getPosition()));
        if (endReason.mayStartNext) {
            Thread.startVirtualThread(() -> advanceAfter(track));
        }
    }

    @Override
    public void onTrackException(AudioPlayer player, AudioTrack track, FriendlyException exception) {
        log.warn("Falha ao tocar {} na guild {}: {}", track.getInfo().uri, guildId, exception.getMessage());
        events.accept(new PlayerEvent.TrackFailed(guildId, queued(track), exception.getMessage()));
    }

    @Override
    public void onTrackStuck(AudioPlayer player, AudioTrack track, long thresholdMs) {
        log.warn("Faixa travada {} na guild {}, pulando", track.getInfo().uri, guildId);
        Thread.startVirtualThread(() -> {
            synchronized (this) {
                if (isCurrent(track)) {
                    skip();
                }
            }
        });
    }

    /** Só avança se ninguém já trocou de faixa nesse meio tempo (ex.: skip ao mesmo tempo). */
    synchronized void advanceAfter(AudioTrack ended) {
        if (isCurrent(ended)) {
            next();
        }
    }

    private boolean isCurrent(AudioTrack track) {
        return current != null && current.track() == track;
    }

    private QueuedTrack pollNext(boolean skipping) {
        if (current != null) {
            if (loopMode == LoopMode.TRACK && !skipping) {
                return current.copy();
            }
            if (loopMode == LoopMode.QUEUE) {
                laneOf(current).add(current.copy());
            }
        }
        if (!priorityLane.isEmpty()) {
            return priorityLane.removeFirst();
        }
        return playlistLane.isEmpty() ? null : playlistLane.removeFirst();
    }

    private void startOrIdle(QueuedTrack following) {
        if (following == null) {
            current = null;
            player.stopTrack();
            events.accept(new PlayerEvent.StateChanged(guildId));
            return;
        }
        start(following);
    }

    private void start(QueuedTrack track) {
        current = track;
        track.track().setUserData(track);
        player.startTrack(track.track(), false);
    }

    private int positionFor(QueuedTrack track) {
        return 1 + priorityLane.size() + (track.fromPlaylist() ? playlistLane.size() : 0);
    }

    private List<QueuedTrack> laneOf(QueuedTrack track) {
        return track.fromPlaylist() ? playlistLane : priorityLane;
    }

    private List<QueuedTrack> laneAt(int index) {
        if (index < 0 || index >= priorityLane.size() + playlistLane.size()) {
            throw new UserFacingException("Posição inválida na fila: " + (index + 1));
        }
        return index < priorityLane.size() ? priorityLane : playlistLane;
    }

    private int indexInLane(int index) {
        return index < priorityLane.size() ? index : index - priorityLane.size();
    }

    private QueuedTrack queued(AudioTrack track) {
        var queued = track.getUserData(QueuedTrack.class);
        return queued != null ? queued : QueuedTrack.single(track, 0);
    }
}
