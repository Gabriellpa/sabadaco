package com.gabriellpa.sabadaco.discord;

import com.gabriellpa.sabadaco.UserFacingException;
import com.gabriellpa.sabadaco.stream.DiscordLogin;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Troca o código OAuth da Atividade pela pessoa, na API do Discord: {@code POST /oauth2/token} com o client secret
 * e depois {@code GET /users/@me} com o token dela. O token não é guardado.
 */
@Slf4j
@Component
public class DiscordOAuthLogin implements DiscordLogin {

    static final String API = "https://discord.com/api/v10";

    private final DiscordActivityProperties properties;
    private final JDA jda;
    private final HttpClient http;
    private final JsonMapper json;
    private final String api;

    @Autowired
    public DiscordOAuthLogin(DiscordActivityProperties properties, @Lazy JDA jda, JsonMapper json) {
        this(properties, jda, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(), json, API);
    }

    DiscordOAuthLogin(DiscordActivityProperties properties, JDA jda, HttpClient http, JsonMapper json, String api) {
        this.properties = properties;
        this.jda = jda;
        this.http = http;
        this.json = json;
        this.api = api;
    }

    @Override
    public boolean isConfigured() {
        return properties.clientSecret() != null && !properties.clientSecret().isBlank();
    }

    @Override
    public String clientId() {
        var configured = properties.clientId();
        return configured != null && !configured.isBlank() ? configured.strip() : jda.getSelfUser().getId();
    }

    @Override
    public DiscordUser exchange(String code) {
        if (!isConfigured()) {
            throw new UserFacingException("A Atividade ainda não foi configurada neste bot (falta o client secret do Discord).");
        }
        if (code == null || code.isBlank()) {
            throw new UserFacingException("O Discord não autorizou a entrada. Feche e abra a Atividade de novo.");
        }
        var token = send(HttpRequest.newBuilder(URI.create(api + "/oauth2/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form(Map.of(
                        "client_id", clientId(),
                        "client_secret", properties.clientSecret().strip(),
                        "grant_type", "authorization_code",
                        "code", code.strip()))))
                .build());
        var accessToken = token.path("access_token").asString("");
        if (accessToken.isEmpty()) {
            throw rejected();
        }
        var user = send(HttpRequest.newBuilder(URI.create(api + "/users/@me"))
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build());
        var id = user.path("id").asString("");
        if (id.isEmpty()) {
            throw rejected();
        }
        var name = user.path("global_name").asString("");
        return new DiscordUser(Long.parseLong(id), name.isBlank() ? user.path("username").asString("Alguém") : name, accessToken);
    }

    private JsonNode send(HttpRequest request) {
        try {
            var response = http.send(HttpRequest.newBuilder(request, (name, value) -> true)
                    .timeout(Duration.ofSeconds(10)).build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                // O corpo do erro do Discord não tem segredo, mas também não ajuda a pessoa: fica só no log
                log.debug("Discord respondeu {} em {}", response.statusCode(), request.uri().getPath());
                throw rejected();
            }
            return json.readTree(response.body());
        } catch (IOException error) {
            log.warn("Falha ao falar com o Discord ({})", request.uri().getPath(), error);
            throw new UserFacingException("Não consegui falar com o Discord agora. Tente de novo.");
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new UserFacingException("Não consegui falar com o Discord agora. Tente de novo.");
        }
    }

    private static UserFacingException rejected() {
        return new UserFacingException("O Discord recusou a entrada. Feche e abra a Atividade de novo.");
    }

    private static String form(Map<String, String> fields) {
        return fields.entrySet().stream()
                .map(field -> URLEncoder.encode(field.getKey(), StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(field.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }
}
