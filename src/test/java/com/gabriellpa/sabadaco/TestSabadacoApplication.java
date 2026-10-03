package com.gabriellpa.sabadaco;

import com.gabriellpa.sabadaco.music.TrackSummary;
import com.gabriellpa.sabadaco.playlist.PlaylistScope;
import com.gabriellpa.sabadaco.playlist.PlaylistService;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Sobe o app sem Discord (JDA simulado) e com playlists de exemplo, para mexer no painel admin:
 * {@code ./gradlew bootTestRun} e abra http://localhost:8080 (usuário admin / senha admin).
 */
public class TestSabadacoApplication {

    public static void main(String[] args) {
        SpringApplication.from(SabadacoApplication::main)
                .with(OfflineDiscord.class)
                .run("--spring.main.allow-bean-definition-overriding=true",
                        "--discord.token=offline",
                        "--sabadaco.admin.password=admin");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class OfflineDiscord {

        private static final long GUILD_ID = 1;
        private static final long USER_ID = 42;

        @Bean
        JDA jda() {
            var guild = mock(Guild.class);
            when(guild.getIdLong()).thenReturn(GUILD_ID);
            when(guild.getName()).thenReturn("Servidor de teste");
            var jda = mock(JDA.class);
            when(jda.getGuilds()).thenReturn(List.of(guild));
            when(jda.getGuildById(GUILD_ID)).thenReturn(guild);
            return jda;
        }

        @Bean
        ApplicationRunner samplePlaylists(PlaylistService playlists) {
            return args -> {
                var classics = playlists.create(USER_ID, GUILD_ID, "Clássicos do Sabadaço", PlaylistScope.GUILD);
                playlists.addTrack(USER_ID, classics.id(), new TrackSummary("Kasino no Sabadaço - 07/10/2006", "SBT",
                        "https://www.youtube.com/watch?v=LCDaw0QmQQc", 245_000, null, false), "kassino");
                playlists.create(USER_ID, GUILD_ID, "Favoritas", PlaylistScope.GLOBAL);
            };
        }
    }
}
