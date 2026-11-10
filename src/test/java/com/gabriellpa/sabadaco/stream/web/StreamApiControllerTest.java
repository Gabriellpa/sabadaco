package com.gabriellpa.sabadaco.stream.web;

import com.gabriellpa.sabadaco.UserFacingException;
import com.gabriellpa.sabadaco.admin.SecurityConfiguration;
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

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Sem login e sem CSRF: a página da stream chama a API com o código. */
@WebMvcTest(StreamApiController.class)
@Import(SecurityConfiguration.class)
class StreamApiControllerTest {

    /** Maior que 2^53: precisa ir como texto no JSON. */
    private static final long STREAM_ID = 9_007_199_254_740_993L;

    @Autowired
    MockMvc mvc;
    @MockitoBean
    StreamService streams;

    @Test
    void exchangesCodeForTokenWithoutLogin() throws Exception {
        var stream = new StreamSession(STREAM_ID, "Valorant", 10, 20, 30, 1, "gabriel", Instant.EPOCH);
        when(streams.redeem(STREAM_ID, "ABCD-EFGH"))
                .thenReturn(new JoinTicket("wss://x", "jwt", "g10-c20", "2", false, stream, List.of(stream)));

        mvc.perform(post("/stream/api/{id}/join", STREAM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"ABCD-EFGH\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt"))
                .andExpect(jsonPath("$.serverUrl").value("wss://x"))
                .andExpect(jsonPath("$.canPublish").value(false))
                .andExpect(jsonPath("$.stream.id").value(String.valueOf(STREAM_ID)))
                .andExpect(jsonPath("$.channelStreams[0].hostName").value("gabriel"));
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
}
