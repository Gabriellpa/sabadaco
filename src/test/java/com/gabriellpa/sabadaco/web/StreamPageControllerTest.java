package com.gabriellpa.sabadaco.web;

import com.gabriellpa.sabadaco.admin.SecurityConfiguration;
import com.gabriellpa.sabadaco.stream.DiscordLogin;
import com.gabriellpa.sabadaco.stream.StreamService;
import com.gabriellpa.sabadaco.stream.StreamSession;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A página do link da DM: pública, renderiza o título e o campo do código. */
@WebMvcTest(StreamPageController.class)
@Import(SecurityConfiguration.class)
class StreamPageControllerTest {

    private static final long STREAM_ID = 9_007_199_254_740_993L;

    @Autowired
    MockMvc mvc;
    @MockitoBean
    StreamService streams;
    @MockitoBean
    DiscordLogin discord;

    @Test
    void liveStreamShowsTitleAndCodeFieldWithoutLogin() throws Exception {
        when(streams.get(STREAM_ID)).thenReturn(Optional.of(
                new StreamSession(STREAM_ID, "Valorant <ranked>", 10, 20, 30, 1, "gabriel", Instant.EPOCH)));

        mvc.perform(get("/stream/{id}", STREAM_ID))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-stream-id=\"" + STREAM_ID + "\"")))
                .andExpect(content().string(containsString("Valorant &lt;ranked&gt;")))
                .andExpect(content().string(containsString("id=\"code\"")))
                .andExpect(content().string(containsString("livekit-client.umd.js")));
    }

    @Test
    void endedStreamHasNoCodeField() throws Exception {
        when(streams.get(STREAM_ID)).thenReturn(Optional.empty());

        mvc.perform(get("/stream/{id}", STREAM_ID))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Esta stream terminou")))
                .andExpect(content().string(not(containsString("id=\"code\""))));
    }

    @Test
    void activityPageLoadsDiscordSdkWithoutCodeFieldAndCanBeFramed() throws Exception {
        when(discord.isConfigured()).thenReturn(true);
        when(discord.clientId()).thenReturn("888603239304527884");

        mvc.perform(get("/stream/activity"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("X-Frame-Options"))
                .andExpect(content().string(containsString("data-mode=\"activity\"")))
                .andExpect(content().string(containsString("data-client-id=\"888603239304527884\"")))
                .andExpect(content().string(containsString("discord-embedded-app-sdk.js")))
                .andExpect(content().string(containsString("id=\"activity-retry\"")))
                .andExpect(content().string(not(containsString("id=\"code\""))));
    }

    @Test
    void activityWithoutSecretHasNoClientId() throws Exception {
        mvc.perform(get("/stream/activity/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-mode=\"activity\"")))
                .andExpect(content().string(not(containsString("data-client-id"))));
    }

    @Test
    void linkPageKeepsCodeFlowAndCannotBeFramed() throws Exception {
        when(streams.get(STREAM_ID)).thenReturn(Optional.empty());

        mvc.perform(get("/stream/{id}", STREAM_ID))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(content().string(containsString("data-mode=\"link\"")))
                .andExpect(content().string(not(containsString("discord-embedded-app-sdk.js"))));
    }

    @Test
    void onlyNumericIds() throws Exception {
        mvc.perform(get("/stream/abc")).andExpect(status().isNotFound());
    }
}
