package com.gabriellpa.sabadaco.playlist;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@ConditionalOnProperty(name = "sabadaco.storage.type", havingValue = "memory", matchIfMissing = true)
public class InMemoryPlaylistRepository implements PlaylistRepository {

    private final Map<String, Playlist> playlists = new ConcurrentHashMap<>();

    @Override
    public Playlist save(Playlist playlist) {
        var saved = playlist.id() != null ? playlist
                : new Playlist(UUID.randomUUID().toString(), playlist.ownerId(), playlist.scope(),
                playlist.guildId(), playlist.name(), playlist.tracks());
        playlists.put(saved.id(), saved);
        return saved;
    }

    @Override
    public Optional<Playlist> findById(String id) {
        return Optional.ofNullable(playlists.get(id));
    }

    @Override
    public List<Playlist> findByOwner(long ownerId) {
        return playlists.values().stream().filter(playlist -> playlist.ownerId() == ownerId).toList();
    }

    @Override
    public List<Playlist> findAll() {
        return List.copyOf(playlists.values());
    }

    @Override
    public void delete(String id) {
        playlists.remove(id);
    }
}
