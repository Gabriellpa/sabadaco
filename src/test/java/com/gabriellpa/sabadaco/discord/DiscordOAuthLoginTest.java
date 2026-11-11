package com.gabriellpa.sabadaco.discord;

import com.gabriellpa.sabadaco.UserFacingException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** A troca do código da Atividade contra um Discord de mentira (servidor HTTP local). */
class DiscordOAuthLoginTest {

    private HttpServer server;
    private final AtomicReference<String> tokenForm = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private volatile int tokenStatus = 200;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/oauth2/token", exchange -> {
            tokenForm.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            reply(exchange, tokenStatus, tokenStatus == 200 ? "{\"access_token\":\"user-token\",\"token_type\":\"Bearer\"}" : "{\"error\":\"invalid_grant\"}");
        });
        server.createContext("/users/@me", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            reply(exchange, 200, "{\"id\":\"334670701011271682\",\"username\":\"gabriel\",\"global_name\":\"Gabriel\"}");
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void exchangesCodeForTheDiscordUser() {
        var user = login("segredo").exchange(" abc ");

        assertThat(user.id()).isEqualTo(334670701011271682L);
        assertThat(user.displayName()).isEqualTo("Gabriel");
        assertThat(user.accessToken()).isEqualTo("user-token");
        assertThat(tokenForm.get()).contains("client_id=888", "client_secret=segredo", "grant_type=authorization_code", "code=abc");
        assertThat(authorization.get()).isEqualTo("Bearer user-token");
    }

    @Test
    void refusedCodeBecomesAFriendlyMessage() {
        tokenStatus = 400;

        assertThatThrownBy(() -> login("segredo").exchange("abc"))
                .isInstanceOf(UserFacingException.class).hasMessageContaining("recusou");
    }

    @Test
    void withoutClientSecretTheActivityIsOff() {
        var login = login(" ");

        assertThat(login.isConfigured()).isFalse();
        assertThatThrownBy(() -> login.exchange("abc"))
                .isInstanceOf(UserFacingException.class).hasMessageContaining("não foi configurada");
        assertThat(tokenForm.get()).isNull();
    }

    private DiscordOAuthLogin login(String secret) {
        return new DiscordOAuthLogin(new DiscordActivityProperties("888", secret), null, HttpClient.newHttpClient(),
                JsonMapper.builder().build(), "http://127.0.0.1:" + server.getAddress().getPort());
    }

    private static void reply(HttpExchange exchange, int status, String body) throws IOException {
        var bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
