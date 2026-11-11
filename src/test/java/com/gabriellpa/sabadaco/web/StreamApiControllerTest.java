package com.gabriellpa.sabadaco.web;

import com.gabriellpa.sabadaco.UserFacingException;
import com.gabriellpa.sabadaco.admin.SecurityConfiguration;
import com.gabriellpa.sabadaco.stream.DiscordLogin;
import com.gabriellpa.sabadaco.stream.DiscordLogin.DiscordUser;
import com.gabriellpa.sabadaco.stream.StreamChatService;
import com.gabriellpa.sabadaco.stream.StreamService;
import com.gabriellpa.sabadaco.stream.StreamService.JoinTicket;
import com.gabriellpa.sabadaco.stream.StreamSession;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Sem login e sem CSRF: a página da stream chama a API com o código e depois com a sessão (Bearer). */
@WebMvcTest(StreamApiController.class)
@Import(SecurityConfiguration.class)
class StreamApiControllerTest {

    /** Maior que 2^53: precisa ir como texto no JSON. */
    private static final long STREAM_ID = 9_007_199_254_740_993L;

    private final StreamSession stream = new StreamSession(STREAM_ID, "Valorant", 10, 20, 30, 1, "gabriel", Instant.EPOCH);

    @Autowired
    MockMvc mvc;
    @MockitoBean
    StreamService streams;
    @MockitoBean
    StreamChatService chat;
    @MockitoBean
    DiscordLogin discord;

    @Test
    void exchangesCodeForTokenAndSessionWithoutLogin() throws Exception {
        when(streams.redeem(STREAM_ID, "ABCD-EFGH"))
                .thenReturn(new JoinTicket("wss://x", "jwt", "g10-c20", "2", false, stream, List.of(stream), "sess"));

        mvc.perform(post("/stream/api/{id}/join", STREAM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"ABCD-EFGH\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt"))
                .andExpect(jsonPath("$.serverUrl").value("wss://x"))
                .andExpect(jsonPath("$.canPublish").value(false))
                .andExpect(jsonPath("$.session").value("sess"))
                .andExpect(jsonPath("$.stream.id").value(String.valueOf(STREAM_ID)))
                .andExpect(jsonPath("$.channelStreams[0].hostName").value("gabriel"));
    }

    @Test
    void resumeAndListUseTheBearerSession() throws Exception {
        when(streams.resume("sess"))
                .thenReturn(new JoinTicket("wss://x", "jwt2", "g10-c20", "1", true, stream, List.of(stream), "sess"));
        when(streams.channelStreams("sess")).thenReturn(List.of(stream));

        mvc.perform(post("/stream/api/resume").header("Authorization", "Bearer sess"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt2"))
                .andExpect(jsonPath("$.canPublish").value(true));
        mvc.perform(get("/stream/api/streams").header("Authorization", "Bearer sess"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(String.valueOf(STREAM_ID)))
                .andExpect(jsonPath("$[0].title").value("Valorant"));
    }

    @Test
    void chatGoesToTheService() throws Exception {
        mvc.perform(post("/stream/api/chat")
                        .header("Authorization", "Bearer sess")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"boa jogada\"}"))
                .andExpect(status().isNoContent());

        verify(chat).fromWeb("sess", "boa jogada");
    }

    @Test
    void withoutSessionTheServiceRefuses() throws Exception {
        doThrow(new UserFacingException("Sua sessão terminou.")).when(chat).fromWeb(isNull(), org.mockito.ArgumentMatchers.anyString());

        mvc.perform(post("/stream/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"oi\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Sua sessão terminou."));
    }

    @Test
    void adminPanelStillRequiresLogin() throws Exception {
        mvc.perform(get("/admin")).andExpect(status().isUnauthorized());
        mvc.perform(post("/admin/qualquer-coisa")).andExpect(status().isForbidden()); // CSRF continua valendo fora da API da stream
    }

    @Test
    void invalidCodeIsForbiddenWithMessage() throws Exception {
        when(streams.redeem(STREAM_ID, "ERRADO")).thenThrow(new UserFacingException("Código inválido ou expirado."));

        mvc.perform(post("/stream/api/{id}/join", STREAM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"ERRADO\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Código inválido ou expirado."));
    }

    @Test
    void activityLoginExchangesDiscordCodeAndJoinsTheChannelStream() throws Exception {
        when(discord.exchange("discord-code")).thenReturn(new DiscordUser(2, "viewer"));
        when(streams.joinFromDiscord(10, 20, 2, "viewer"))
                .thenReturn(new JoinTicket("wss://x", "jwt", "g10-c20", "2", false, stream, List.of(stream), "sess"));

        mvc.perform(post("/stream/api/activity/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"discord-code\",\"guildId\":\"10\",\"channelId\":\"20\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.token").value("jwt"))
                .andExpect(jsonPath("$.session").value("sess"));
    }

    @Test
    void insideDiscordTheProxyPathIsForwardedToTheApi() throws Exception {
        // A raiz da Atividade é /stream/activity, então a página chama a API por /stream/activity/stream/api/...
        mvc.perform(post("/stream/activity/stream/api/activity/login?x=1").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(forwardedUrl("/stream/api/activity/login?x=1"));
        mvc.perform(get("/stream/activity/js/stream.js")).andExpect(forwardedUrl("/js/stream.js"));
    }

    @Test
    void activityLoginOutsideAVoiceChannelIsRefused() throws Exception {
        mvc.perform(post("/stream/api/activity/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"discord-code\",\"guildId\":\"10\",\"channelId\":null}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Abra a Atividade dentro de um canal de voz."));
        verify(discord, never()).exchange(anyString());
    }

    @Test
    void activityPathOnlyReachesTheStreamPage() throws Exception {
        mvc.perform(get("/stream/activity/admin")).andExpect(status().isNotFound());
        mvc.perform(get("/stream/activity/stream/../admin")).andExpect(status().isNotFound());
        mvc.perform(get("/stream/activity/actuator/health")).andExpect(status().isNotFound());
        mvc.perform(post("/stream/activity/admin/qualquer-coisa")).andExpect(status().isNotFound());
    }
}
