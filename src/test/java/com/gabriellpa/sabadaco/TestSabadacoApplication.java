package com.gabriellpa.sabadaco;

import com.gabriellpa.sabadaco.discord.DiscordDirectory;
import com.gabriellpa.sabadaco.discord.interaction.InteractionExecuted;
import com.gabriellpa.sabadaco.music.TrackSummary;
import com.gabriellpa.sabadaco.playlist.PlaylistScope;
import com.gabriellpa.sabadaco.playlist.PlaylistService;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.MeterRegistry;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.managers.AudioManager;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Sobe o app sem Discord (JDA simulado), com playlists de exemplo e tráfego simulado para os gráficos:
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
        private static final long[] USERS = {42, 7, 99, 1234};
        private static final String[] NAMES = {"gabriel", "leão", "kasino", "sabadeiro"};
        private static final String[] COMMANDS = {"play", "search", "skip", "queue", "playlist play", "player:pause", "help", "volume"};

        @Bean
        JDA jda() {
            var guild = mock(Guild.class);
            when(guild.getIdLong()).thenReturn(GUILD_ID);
            when(guild.getName()).thenReturn("Servidor de teste");
            when(guild.getAudioManager()).thenReturn(mock(AudioManager.class));
            var jda = mock(JDA.class);
            when(jda.getGuilds()).thenReturn(List.of(guild));
            when(jda.getGuildById(GUILD_ID)).thenReturn(guild);
            when(jda.getGatewayPing()).thenReturn(42L);
            return jda;
        }

        @Bean
        ApplicationRunner samplePlaylists(PlaylistService playlists) {
            return args -> {
                var classics = playlists.create(USERS[0], GUILD_ID, "Clássicos do Sabadaço", PlaylistScope.GUILD);
                playlists.addTrack(USERS[0], classics.id(), new TrackSummary("Kasino – Can't Get Over ft. Gilberto Barros (Versão Sabadaço)", "Cláudio Vieira",
                        "https://www.youtube.com/watch?v=RGqH4mfmtkQ", 210_000, null, false), "kassino");
                playlists.addTrack(USERS[0], classics.id(), new TrackSummary("Kasino no Sabadaço - 07/10/2006", "Teleguiado",
                        "https://www.youtube.com/watch?v=LCDaw0QmQQc", 210_000, null, false), null);
                playlists.create(USERS[0], GUILD_ID, "Favoritas", PlaylistScope.GLOBAL);
                playlists.create(USERS[1], GUILD_ID, "Pagode do Leão", PlaylistScope.GUILD);
            };
        }

        /** Tráfego falso só para dar vida aos gráficos no modo offline. */
        @Bean
        ApplicationRunner demoTraffic(ApplicationEventPublisher events, MeterRegistry registry, DiscordDirectory directory) {
            return args -> {
                for (int i = 0; i < USERS.length; i++) {
                    var user = mock(User.class);
                    when(user.getIdLong()).thenReturn(USERS[i]);
                    when(user.getEffectiveName()).thenReturn(NAMES[i]);
                    directory.remember(user);
                }
                var sent = new AtomicLong();
                FunctionCounter.builder("sabadaco.audio.sent.bytes", sent, AtomicLong::get).tag("guild", "demo").register(registry);
                Thread.ofVirtual().name("demo-traffic").start(() -> {
                    var random = ThreadLocalRandom.current();
                    while (!Thread.currentThread().isInterrupted()) {
                        try {
                            TimeUnit.MILLISECONDS.sleep(400);
                        } catch (InterruptedException e) {
                            return;
                        }
                        sent.addAndGet(random.nextInt(5_000, 8_500));
                        registry.counter("sabadaco.audio.downloaded.bytes", "source", "youtube").increment(random.nextInt(2_000, 12_000));
                        if (random.nextInt(4) == 0) {
                            var command = COMMANDS[random.nextInt(COMMANDS.length)];
                            boolean component = command.contains(":");
                            var outcome = random.nextInt(15) == 0 ? "user_error" : "success";
                            long nanos = TimeUnit.MILLISECONDS.toNanos(random.nextLong(40, command.startsWith("play") || command.equals("search") ? 1_400 : 300));
                            registry.timer(component ? "sabadaco.components" : "sabadaco.commands", component ? "component" : "command", command, "outcome", outcome)
                                    .record(nanos, TimeUnit.NANOSECONDS);
                            events.publishEvent(new InteractionExecuted(USERS[random.nextInt(USERS.length)], command, component, outcome, nanos));
                            if (command.equals("search")) {
                                registry.counter("sabadaco.searches", "outcome", random.nextBoolean() ? "found" : "cached").increment();
                            }
                        }
                        if (random.nextInt(20) == 0) {
                            registry.counter("sabadaco.tracks.played", "source", "youtube", "origin", random.nextBoolean() ? "single" : "playlist").increment();
                        }
                    }
                });
            };
        }
    }
}
