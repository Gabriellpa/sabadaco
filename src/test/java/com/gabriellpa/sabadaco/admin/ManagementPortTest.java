package com.gabriellpa.sabadaco.admin;

import net.dv8tion.jda.api.JDA;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/** O Prometheus coleta sem login só pela porta de gerenciamento; o painel continua protegido. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"discord.token=test", "management.server.port=0"})
class ManagementPortTest {

    @MockitoBean
    JDA jda;

    @Value("${local.server.port}")
    int appPort;

    @Value("${local.management.port}")
    int managementPort;

    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    @Test
    void prometheusIsOpenOnTheManagementPort() throws Exception {
        var response = get(managementPort, "/actuator/prometheus");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("sabadaco_discord_guilds", "sabadaco_users_active");
    }

    @Test
    void otherActuatorEndpointsOnTheManagementPortStillNeedLogin() throws Exception {
        assertThat(get(managementPort, "/actuator/metrics").statusCode()).isEqualTo(401);
    }

    @Test
    void adminPanelStillNeedsLogin() throws Exception {
        assertThat(get(appPort, "/admin").statusCode()).isIn(302, 401);
    }

    private HttpResponse<String> get(int port, String path) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
