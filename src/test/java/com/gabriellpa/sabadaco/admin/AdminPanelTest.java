package com.gabriellpa.sabadaco.admin;

import com.gabriellpa.sabadaco.UserFacingException;
import com.gabriellpa.sabadaco.discord.DiscordDirectory;
import com.gabriellpa.sabadaco.discord.DiscordDirectory.GuildInfo;
import com.gabriellpa.sabadaco.metrics.ActivityTracker;
import com.gabriellpa.sabadaco.metrics.MetricsSampler;
import com.gabriellpa.sabadaco.music.GuildPlayerRegistry;
import com.gabriellpa.sabadaco.music.LoopMode;
import com.gabriellpa.sabadaco.music.MusicService;
import com.gabriellpa.sabadaco.music.PlayerSnapshot;
import com.gabriellpa.sabadaco.music.QueueEntry;
import com.gabriellpa.sabadaco.music.TrackSummary;
import com.gabriellpa.sabadaco.playlist.Playlist;
import com.gabriellpa.sabadaco.playlist.PlaylistScope;
import com.gabriellpa.sabadaco.playlist.PlaylistService;
import com.gabriellpa.sabadaco.playlist.PlaylistTrack;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Renderiza as telas de verdade (Thymeleaf) com serviços mockados. */
@WebMvcTest
@Import({SecurityConfiguration.class, AdminViews.class, AdminPanelTest.Metrics.class})
class AdminPanelTest {

    private static final long GUILD = 10;

    @TestConfiguration
    static class Metrics {
        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    @Autowired
    MockMvc mvc;
    @MockitoBean
    MusicService musicService;
    @MockitoBean
    PlaylistService playlistService;
    @MockitoBean
    DiscordDirectory directory;
    @MockitoBean
    GuildPlayerRegistry players;
    @MockitoBean
    MetricsSampler sampler;
    @MockitoBean
    ActivityTracker activity;

    private final TrackSummary song = new TrackSummary("Kasino no Sabadaço", "SBT", "https://youtu.be/LCDaw0QmQQc", 200_000,
            "https://i.ytimg.com/vi/LCDaw0QmQQc/hqdefault.jpg", false);
    private final Playlist playlist = new Playlist("p1", 42, PlaylistScope.GUILD, GUILD, "Clássicos",
            List.of(new PlaylistTrack(song.uri(), song.title(), song.author(), song.durationMs(), "kassino")));

    @BeforeEach
    void setUp() {
        var snapshot = new PlayerSnapshot(GUILD, new QueueEntry(song, 42, null), 60_000, false, 100, LoopMode.OFF,
                List.of(new QueueEntry(song, 42, null)), List.of(new QueueEntry(song, 7, "Clássicos")), 2048);
        when(directory.guilds()).thenReturn(List.of(new GuildInfo(GUILD, "Sabadaço")));
        when(directory.guildName(anyLong())).thenReturn("Sabadaço");
        when(directory.userName(anyLong())).thenReturn("gabriel");
        when(directory.knownUsers()).thenReturn(Map.of(42L, "gabriel"));
        when(directory.voiceChannels(GUILD)).thenReturn(List.of(new GuildInfo(1, "Geral")));
        when(musicService.snapshot(GUILD)).thenReturn(Optional.of(snapshot));
        when(playlistService.search(null, GUILD)).thenReturn(List.of(playlist));
        when(playlistService.search(null, null)).thenReturn(List.of(playlist));
        when(playlistService.search(42L, null)).thenReturn(List.of(playlist));
        when(playlistService.get("p1")).thenReturn(playlist);
    }

    @Test
    void requiresLogin() throws Exception {
        // Navegador (Accept: text/html) vai para o login; clientes de API (Prometheus) recebem 401 do basic auth
        mvc.perform(get("/admin").accept(MediaType.TEXT_HTML))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
        mvc.perform(get("/actuator/prometheus")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void dashboardShowsWhatIsPlaying() throws Exception {
        mvc.perform(get("/admin"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Kasino no Sabadaço")))
                .andExpect(content().string(containsString("vol 100% · 2,0 KB")));
    }

    @Test
    @WithMockUser
    void guildPageShowsQueueWithOrigin() throws Exception {
        mvc.perform(get("/admin/guilds/{id}", GUILD))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Fila (2)")))
                .andExpect(content().string(containsString("📜 Clássicos")))
                .andExpect(content().string(containsString("Geral")));
    }

    @Test
    @WithMockUser
    void controlsCallTheMusicServiceAndReturnTheFragment() throws Exception {
        mvc.perform(post("/admin/guilds/{id}/control/pause", GUILD).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"state\"")));
        verify(musicService).togglePause(GUILD);
    }

    @Test
    @WithMockUser
    void businessErrorsBecomeToastForHtmx() throws Exception {
        when(musicService.togglePause(GUILD)).thenThrow(new UserFacingException("Nada tocando neste servidor."));

        mvc.perform(post("/admin/guilds/{id}/control/pause", GUILD).with(csrf()).header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andExpect(header().string("HX-Retarget", "#toast"))
                .andExpect(content().string(containsString("Nada tocando neste servidor.")));
    }

    @Test
    @WithMockUser
    void playlistPagesRender() throws Exception {
        mvc.perform(get("/admin/playlists"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Clássicos")));
        mvc.perform(get("/admin/playlists/p1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("kassino")));
    }

    @Test
    @WithMockUser
    void movingATrackActsOnBehalfOfTheOwner() throws Exception {
        mvc.perform(post("/admin/playlists/p1/tracks/0/move").param("to", "p2").with(csrf()))
                .andExpect(status().is3xxRedirection());
        verify(playlistService).moveTrack(42, "p1", 0, "p2");
    }
}
