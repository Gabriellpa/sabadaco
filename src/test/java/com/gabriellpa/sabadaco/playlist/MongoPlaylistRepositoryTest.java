package com.gabriellpa.sabadaco.playlist;

import com.mongodb.client.MongoClients;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Contra um MongoDB de verdade (Testcontainers); pulado se não houver Docker. */
@Testcontainers(disabledWithoutDocker = true)
class MongoPlaylistRepositoryTest {

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:8.2");

    @Test
    void roundTripsPlaylistWithTracks() {
        try (var client = MongoClients.create(MONGO.getConnectionString())) {
            var repository = new MongoPlaylistRepository(new MongoTemplate(client, "sabadaco-test"));
            var track = new PlaylistTrack("https://youtu.be/x", "Abertura", "Kasino", 210_000, "kassino");

            var saved = repository.save(new Playlist(null, 1, PlaylistScope.GUILD, 100L, "Rock", List.of(track)));
            repository.save(new Playlist(null, 1, PlaylistScope.GLOBAL, null, "Favoritas", List.of()));
            repository.save(new Playlist(null, 2, PlaylistScope.GUILD, 100L, "Do Bob", List.of()));

            assertThat(repository.findById(saved.id())).contains(saved);
            assertThat(repository.findByOwner(1)).extracting(Playlist::name).containsExactlyInAnyOrder("Rock", "Favoritas");
            assertThat(repository.findAll()).hasSize(3);

            repository.save(saved.withName("Rock pesado"));
            assertThat(repository.findById(saved.id())).map(Playlist::name).contains("Rock pesado");

            repository.delete(saved.id());
            assertThat(repository.findById(saved.id())).isEmpty();
        }
    }
}
