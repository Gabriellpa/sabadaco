package com.gabriellpa.sabadaco.playlist;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Playlists no MongoDB: um documento por playlist, com as músicas dentro (é assim que o
 * {@link PlaylistService} já lê e grava). Criada por {@link MongoStorageConfiguration}.
 */
public class MongoPlaylistRepository implements PlaylistRepository {

    static final String COLLECTION = "playlists";

    private final MongoTemplate mongo;

    public MongoPlaylistRepository(MongoTemplate mongo) {
        this.mongo = mongo;
        var indexes = mongo.indexOps(COLLECTION);
        indexes.createIndex(new Index("ownerId", Sort.Direction.ASC));
        indexes.createIndex(new Index("guildId", Sort.Direction.ASC));
    }

    @Override
    public Playlist save(Playlist playlist) {
        var saved = playlist.id() != null ? playlist
                : new Playlist(UUID.randomUUID().toString(), playlist.ownerId(), playlist.scope(),
                playlist.guildId(), playlist.name(), playlist.tracks());
        return mongo.save(saved, COLLECTION);
    }

    @Override
    public Optional<Playlist> findById(String id) {
        return Optional.ofNullable(mongo.findById(id, Playlist.class, COLLECTION));
    }

    @Override
    public List<Playlist> findByOwner(long ownerId) {
        return mongo.find(Query.query(Criteria.where("ownerId").is(ownerId)), Playlist.class, COLLECTION);
    }

    @Override
    public List<Playlist> findAll() {
        return mongo.findAll(Playlist.class, COLLECTION);
    }

    @Override
    public void delete(String id) {
        mongo.remove(Query.query(Criteria.where("_id").is(id)), COLLECTION);
    }
}
