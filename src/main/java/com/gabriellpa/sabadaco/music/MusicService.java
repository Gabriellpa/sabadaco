package com.gabriellpa.sabadaco.music;

import com.gabriellpa.sabadaco.UserFacingException;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Fachada de música usada pelo Discord e pelo painel admin. Não depende de JDA nem de web.
 */
@Service
@RequiredArgsConstructor
public class MusicService {

    public static final int MAX_VOLUME = 150;

    private final GuildPlayerRegistry players;
    private final TrackLoader trackLoader;
    private final VoiceGateway voiceGateway;

    /** Música avulsa (URL ou busca): entra na fila prioritária. Playlists do YouTube entram como playlist. */
    public EnqueueResult play(long guildId, long voiceChannelId, long requesterId, String query) {
        var player = players.getOrCreate(guildId);
        var tracks = switch (trackLoader.loadNow(player, query)) {
            case LoadResult.TrackLoaded loaded -> List.of(QueuedTrack.single(loaded.track(), requesterId));
            case LoadResult.PlaylistLoaded playlist when playlist.searchResult() ->
                    List.of(QueuedTrack.single(firstOf(playlist.tracks(), query), requesterId));
            case LoadResult.PlaylistLoaded playlist -> playlist.tracks().stream()
                    .map(track -> QueuedTrack.fromPlaylist(track, requesterId, playlist.name()))
                    .toList();
            case LoadResult.NoMatches ignored -> throw new UserFacingException("Nenhuma música encontrada para: " + query);
            case LoadResult.Failed failed -> throw new UserFacingException("Erro ao carregar música: " + failed.message());
        };
        connect(player, voiceChannelId);
        return player.getScheduler().enqueue(tracks);
    }

    /** Toca uma playlist salva: todas as faixas entram na fila de playlist (depois das avulsas). */
    public EnqueueResult playPlaylist(long guildId, long voiceChannelId, long requesterId,
                                      String playlistName, List<String> uris, boolean shuffle) {
        if (uris.isEmpty()) {
            throw new UserFacingException("A playlist " + playlistName + " está vazia.");
        }
        var player = players.getOrCreate(guildId);
        var futures = uris.stream().map(uri -> trackLoader.load(new Object(), uri)).toList();
        var tracks = new ArrayList<QueuedTrack>();
        for (int i = 0; i < futures.size(); i++) {
            if (TrackLoader.await(futures.get(i), uris.get(i)) instanceof LoadResult.TrackLoaded loaded) {
                tracks.add(QueuedTrack.fromPlaylist(loaded.track(), requesterId, playlistName));
            }
        }
        if (tracks.isEmpty()) {
            throw new UserFacingException("Nenhuma faixa da playlist " + playlistName + " pôde ser carregada.");
        }
        if (shuffle) {
            Collections.shuffle(tracks);
        }
        connect(player, voiceChannelId);
        return player.getScheduler().enqueue(tracks).withFailed(uris.size() - tracks.size());
    }

    /** Resolve uma URL/busca para salvar em playlist, sem tocar. */
    public TrackSummary resolve(String query) {
        return switch (trackLoader.loadNow(new Object(), query)) {
            case LoadResult.TrackLoaded loaded -> TrackSummary.of(loaded.track());
            case LoadResult.PlaylistLoaded playlist -> TrackSummary.of(firstOf(playlist.tracks(), query));
            case LoadResult.NoMatches ignored -> throw new UserFacingException("Nenhuma música encontrada para: " + query);
            case LoadResult.Failed failed -> throw new UserFacingException("Erro ao carregar música: " + failed.message());
        };
    }

    public boolean togglePause(long guildId) {
        var player = require(guildId);
        boolean paused = !player.getAudioPlayer().isPaused();
        player.getAudioPlayer().setPaused(paused);
        player.getScheduler().onStateChanged();
        return paused;
    }

    public void skip(long guildId) {
        require(guildId).getScheduler().skip();
    }

    public void stop(long guildId) {
        players.find(guildId).ifPresent(player -> player.getScheduler().stop());
        voiceGateway.disconnect(guildId);
    }

    public int setVolume(long guildId, int volume) {
        var player = require(guildId);
        int clamped = Math.clamp(volume, 0, MAX_VOLUME);
        player.getAudioPlayer().setVolume(clamped);
        player.getScheduler().onStateChanged();
        return clamped;
    }

    public LoopMode cycleLoop(long guildId) {
        return require(guildId).getScheduler().cycleLoop();
    }

    public void shuffle(long guildId) {
        require(guildId).getScheduler().shuffle();
    }

    public QueueEntry removeFromQueue(long guildId, int index) {
        return require(guildId).getScheduler().remove(index);
    }

    public void moveInQueue(long guildId, int from, int to) {
        require(guildId).getScheduler().move(from, to);
    }

    public void clearQueue(long guildId) {
        require(guildId).getScheduler().clear();
    }

    public Optional<PlayerSnapshot> snapshot(long guildId) {
        return players.find(guildId).map(GuildPlayer::snapshot);
    }

    public List<PlayerSnapshot> snapshots() {
        return players.all().stream()
                .map(GuildPlayer::snapshot)
                .sorted(Comparator.comparing(PlayerSnapshot::playing).reversed())
                .toList();
    }

    private void connect(GuildPlayer player, long voiceChannelId) {
        if (!voiceGateway.isConnected(player.getGuildId())) {
            voiceGateway.connect(player.getGuildId(), voiceChannelId, player);
        }
    }

    private GuildPlayer require(long guildId) {
        return players.find(guildId)
                .orElseThrow(() -> new UserFacingException("Nada tocando neste servidor."));
    }

    private static AudioTrack firstOf(List<AudioTrack> tracks, String query) {
        if (tracks.isEmpty()) {
            throw new UserFacingException("Nenhuma música encontrada para: " + query);
        }
        return tracks.getFirst();
    }
}
