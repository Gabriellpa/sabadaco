package com.gabriellpa.sabadaco.playlist;

import com.mongodb.ConnectionString;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * Liga o MongoDB quando {@code sabadaco.storage.type=mongo}. O cliente é criado aqui, e não pela
 * auto-configuração do Spring Boot, para o modo {@code memory} (dev, testes) não tentar conectar.
 * O banco vem da própria URI ({@code mongodb://usuario:senha@host:27017/sabadaco?authSource=admin}).
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "sabadaco.storage.type", havingValue = "mongo")
class MongoStorageConfiguration {

    @Bean(destroyMethod = "close")
    MongoClient mongoClient(@Value("${sabadaco.storage.mongo-uri}") String uri) {
        return MongoClients.create(uri);
    }

    @Bean
    MongoTemplate mongoTemplate(MongoClient client, @Value("${sabadaco.storage.mongo-uri}") String uri) {
        var database = new ConnectionString(uri).getDatabase();
        return new MongoTemplate(client, database == null ? "sabadaco" : database);
    }

    @Bean
    PlaylistRepository mongoPlaylistRepository(MongoTemplate mongo) {
        return new MongoPlaylistRepository(mongo);
    }
}
