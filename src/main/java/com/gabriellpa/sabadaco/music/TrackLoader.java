package com.gabriellpa.sabadaco.music;

import com.gabriellpa.sabadaco.UserFacingException;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Transforma o callback do Lavaplayer em {@link CompletableFuture}.
 */
@Component
@RequiredArgsConstructor
public class TrackLoader {

    public static final String SEARCH_PREFIX = "ytsearch:";
    private static final long TIMEOUT_SECONDS = 20;

    private final AudioPlayerManager audioPlayerManager;

    /**
     * @param orderingKey loads com a mesma chave são resolvidos em ordem (usamos o player do servidor)
     * @param query       URL ou texto livre (texto vira busca no YouTube)
     */
    public CompletableFuture<LoadResult> load(Object orderingKey, String query) {
        var future = new CompletableFuture<LoadResult>();
        audioPlayerManager.loadItemOrdered(orderingKey, toIdentifier(query), new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                future.complete(new LoadResult.TrackLoaded(track));
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                future.complete(new LoadResult.PlaylistLoaded(playlist.getName(), playlist.getTracks(), playlist.isSearchResult()));
            }

            @Override
            public void noMatches() {
                future.complete(new LoadResult.NoMatches());
            }

            @Override
            public void loadFailed(FriendlyException exception) {
                future.complete(new LoadResult.Failed(exception.getMessage()));
            }
        });
        return future;
    }

    public LoadResult loadNow(Object orderingKey, String query) {
        return await(load(orderingKey, query), query);
    }

    static LoadResult await(CompletableFuture<LoadResult> future, String query) {
        try {
            return future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            return new LoadResult.Failed("demorou demais para carregar " + query);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UserFacingException("Carregamento interrompido.");
        } catch (ExecutionException e) {
            return new LoadResult.Failed(e.getCause().getMessage());
        }
    }

    /** URLs e prefixos de busca passam direto; texto livre vira busca no YouTube. */
    static String toIdentifier(String query) {
        var trimmed = query.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.matches("^[a-z]+search:.*")) {
            return trimmed;
        }
        return SEARCH_PREFIX + trimmed;
    }
}
