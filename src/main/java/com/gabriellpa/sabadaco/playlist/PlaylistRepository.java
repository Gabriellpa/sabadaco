package com.gabriellpa.sabadaco.playlist;

import java.util.List;
import java.util.Optional;

/**
 * Porta de persistência das playlists. Para trocar de armazenamento (SQL, NoSQL...), crie outra
 * implementação condicionada a {@code sabadaco.storage.type} e mude a propriedade.
 */
public interface PlaylistRepository {

    /** Cria (id nulo) ou substitui a playlist. */
    Playlist save(Playlist playlist);

    Optional<Playlist> findById(String id);

    List<Playlist> findByOwner(long ownerId);

    List<Playlist> findAll();

    void delete(String id);
}
