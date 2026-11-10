package com.gabriellpa.sabadaco.stream;

import com.gabriellpa.sabadaco.DiscordAdmins;
import com.gabriellpa.sabadaco.UserFacingException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StreamServiceTest {

    private static final long GUILD = 10;
    private static final long OTHER_GUILD = 11;
    private static final long VOICE = 20;
    private static final long OTHER_VOICE = 21;
    private static final long TEXT = 30;
    private static final long HOST = 1;
    private static final long VIEWER = 2;
    private static final long ADMIN = 3;
    private static final long HOST_2 = 4;

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-10T20:00:00Z"));
    private final FakePresence presence = new FakePresence();
    private final FakeMedia media = new FakeMedia();
    private final StreamService service = new StreamService(new SnowflakeGenerator(clock, 0),
            new InMemoryJoinCodeIssuer(clock, Duration.ofMinutes(5)), media, presence, DiscordAdmins.of(ADMIN),
            clock, "https://sabadaco.example/");

    @Test
    void createOpensStreamInHostsVoiceChannelWithLinkAndCode() {
        presence.put(GUILD, HOST, VOICE);

        var invite = service.create(GUILD, TEXT, HOST, "gabriel", "  Valorant  ");

        assertThat(invite.stream().title()).isEqualTo("Valorant");
        assertThat(invite.stream().voiceChannelId()).isEqualTo(VOICE);
        assertThat(invite.stream().textChannelId()).isEqualTo(TEXT);
        assertThat(invite.link()).isEqualTo("https://sabadaco.example/stream/" + invite.stream().id());
        assertThat(invite.code().code()).isNotBlank();
    }

    @Test
    void createRequiresVoiceChannelTitleAndOneStreamPerHost() {
        assertThatThrownBy(() -> service.create(GUILD, TEXT, HOST, "gabriel", "x"))
                .isInstanceOf(UserFacingException.class).hasMessageContaining("canal de voz");

        presence.put(GUILD, HOST, VOICE);
        assertThatThrownBy(() -> service.create(GUILD, TEXT, HOST, "gabriel", "   "))
                .isInstanceOf(UserFacingException.class);
        assertThatThrownBy(() -> service.create(GUILD, TEXT, HOST, "gabriel", "a".repeat(101)))
                .isInstanceOf(UserFacingException.class);

        service.create(GUILD, TEXT, HOST, "gabriel", "Um");
        assertThatThrownBy(() -> service.create(GUILD, TEXT, HOST, "gabriel", "Dois"))
                .isInstanceOf(UserFacingException.class).hasMessageContaining("já tem uma stream");
    }

    @Test
    void severalStreamsPerChannelButTitlesAreUnique() {
        presence.put(GUILD, HOST, VOICE);
        presence.put(GUILD, HOST_2, VOICE);
        service.create(GUILD, TEXT, HOST, "gabriel", "Valorant");

        assertThatThrownBy(() -> service.create(GUILD, TEXT, HOST_2, "leão", "valorant"))
                .isInstanceOf(UserFacingException.class);
        service.create(GUILD, TEXT, HOST_2, "leão", "Minecraft");

        assertThat(service.streamsIn(GUILD, VOICE)).extracting(StreamSession::title).containsExactly("Valorant", "Minecraft");
    }

    @Test
    void joinFindsByTitleHostNameOrHostIdInViewersChannel() {
        presence.put(GUILD, HOST, VOICE);
        presence.put(GUILD, HOST_2, VOICE);
        presence.put(GUILD, VIEWER, VOICE);
        service.create(GUILD, TEXT, HOST, "gabriel", "Valorant");
        service.create(GUILD, TEXT, HOST_2, "leão", "Minecraft");

        assertThat(service.join(GUILD, VIEWER, "viewer", "minecraft").stream().hostUserId()).isEqualTo(HOST_2);
        assertThat(service.join(GUILD, VIEWER, "viewer", "Gabriel").stream().title()).isEqualTo("Valorant");
        assertThat(service.join(GUILD, VIEWER, "viewer", String.valueOf(HOST_2)).stream().title()).isEqualTo("Minecraft");
        assertThat(service.join(GUILD, VIEWER, "viewer", "mine").stream().title()).isEqualTo("Minecraft");
        assertThat(service.join(GUILD, VIEWER, "viewer", null).stream().title()).isEqualTo("Valorant");
        assertThatThrownBy(() -> service.join(GUILD, VIEWER, "viewer", "fortnite"))
                .isInstanceOf(UserFacingException.class);
    }

    @Test
    void joinOnlySeesStreamsOfTheViewersOwnVoiceChannel() {
        presence.put(GUILD, HOST, VOICE);
        presence.put(GUILD, VIEWER, OTHER_VOICE);
        service.create(GUILD, TEXT, HOST, "gabriel", "Valorant");

        assertThatThrownBy(() -> service.join(GUILD, VIEWER, "viewer", "Valorant"))
                .isInstanceOf(UserFacingException.class).hasMessageContaining("Não há stream");
    }

    @Test
    void redeemGivesViewerTokenForTheChannelRoom() {
        presence.put(GUILD, HOST, VOICE);
        presence.put(GUILD, VIEWER, VOICE);
        var stream = service.create(GUILD, TEXT, HOST, "gabriel", "Valorant").stream();
        var code = service.join(GUILD, VIEWER, "viewer", "Valorant").code().code();

        var ticket = service.redeem(stream.id(), code);

        assertThat(ticket.canPublish()).isFalse();
        assertThat(ticket.room()).isEqualTo("g10-c20");
        assertThat(ticket.identity()).isEqualTo(String.valueOf(VIEWER));
        assertThat(ticket.token()).isEqualTo("token:g10-c20:2:viewer:false");
        assertThat(ticket.serverUrl()).isEqualTo("wss://media.example");
    }

    @Test
    void hostCanPublish() {
        presence.put(GUILD, HOST, VOICE);
        var invite = service.create(GUILD, TEXT, HOST, "gabriel", "Valorant");

        assertThat(service.redeem(invite.stream().id(), invite.code().code()).canPublish()).isTrue();
    }

    @Test
    void oneCodeOpensEveryStreamOfTheChannelButOnlyOnce() {
        presence.put(GUILD, HOST, VOICE);
        presence.put(GUILD, HOST_2, VOICE);
        presence.put(GUILD, VIEWER, VOICE);
        service.create(GUILD, TEXT, HOST, "gabriel", "Valorant");
        var minecraft = service.create(GUILD, TEXT, HOST_2, "leão", "Minecraft").stream();
        var code = service.join(GUILD, VIEWER, "viewer", "Valorant").code().code();

        var ticket = service.redeem(minecraft.id(), code);

        assertThat(ticket.channelStreams()).extracting(StreamSession::title).containsExactly("Valorant", "Minecraft");
        assertThatThrownBy(() -> service.redeem(minecraft.id(), code))
                .isInstanceOf(UserFacingException.class).hasMessageContaining("inválido ou expirado");
    }

    @Test
    void codeFromAnotherChannelDoesNotOpenTheStream() {
        presence.put(GUILD, HOST, VOICE);
        presence.put(GUILD, HOST_2, OTHER_VOICE);
        var valorant = service.create(GUILD, TEXT, HOST, "gabriel", "Valorant").stream();
        var otherChannelCode = service.create(GUILD, TEXT, HOST_2, "leão", "Minecraft").code().code();

        assertThatThrownBy(() -> service.redeem(valorant.id(), otherChannelCode))
                .isInstanceOf(UserFacingException.class).hasMessageContaining("não vale para esta stream");
    }

    @Test
    void viewerMustStillBeInTheVoiceChannelWhenRedeeming() {
        presence.put(GUILD, HOST, VOICE);
        presence.put(GUILD, VIEWER, VOICE);
        var stream = service.create(GUILD, TEXT, HOST, "gabriel", "Valorant").stream();
        var code = service.join(GUILD, VIEWER, "viewer", "").code().code();
        presence.put(GUILD, VIEWER, OTHER_VOICE);

        assertThatThrownBy(() -> service.redeem(stream.id(), code))
                .isInstanceOf(UserFacingException.class).hasMessageContaining("Entre no canal de voz");
    }

    @Test
    void sameIdsInAnotherGuildDoNotMatch() {
        presence.put(GUILD, HOST, VOICE);
        presence.put(OTHER_GUILD, HOST_2, VOICE);
        var stream = service.create(GUILD, TEXT, HOST, "gabriel", "Valorant").stream();
        var foreignCode = service.create(OTHER_GUILD, TEXT, HOST_2, "leão", "Valorant").code().code();

        assertThatThrownBy(() -> service.redeem(stream.id(), foreignCode)).isInstanceOf(UserFacingException.class);
    }

    @Test
    void codeExpiresAfterFiveMinutes() {
        presence.put(GUILD, HOST, VOICE);
        var invite = service.create(GUILD, TEXT, HOST, "gabriel", "Valorant");
        clock.advance(Duration.ofMinutes(5));

        assertThatThrownBy(() -> service.redeem(invite.stream().id(), invite.code().code()))
                .isInstanceOf(UserFacingException.class);
    }

    @Test
    void onlyHostOrAdminEnds() {
        presence.put(GUILD, HOST, VOICE);
        var stream = service.create(GUILD, TEXT, HOST, "gabriel", "Valorant").stream();

        assertThatThrownBy(() -> service.end(stream.id(), VIEWER)).isInstanceOf(UserFacingException.class);
        service.end(stream.id(), ADMIN);

        assertThat(service.get(stream.id())).isEmpty();
    }

    @Test
    void leavingTheVoiceChannelEndsHostedStreamAndRevokesCodes() {
        presence.put(GUILD, HOST, VOICE);
        presence.put(GUILD, VIEWER, VOICE);
        var stream = service.create(GUILD, TEXT, HOST, "gabriel", "Valorant").stream();
        var viewerCode = service.join(GUILD, VIEWER, "viewer", "").code().code();

        assertThat(service.leftVoiceChannel(GUILD, VIEWER)).isEmpty();
        assertThatThrownBy(() -> service.redeem(stream.id(), viewerCode)).isInstanceOf(UserFacingException.class);

        assertThat(service.leftVoiceChannel(GUILD, HOST)).containsExactly(stream);
        assertThat(service.streamsIn(GUILD, VOICE)).isEmpty();
    }

    @Test
    void refusesWhenMediaServerIsNotConfigured() {
        presence.put(GUILD, HOST, VOICE);
        media.configured = false;

        assertThatThrownBy(() -> service.create(GUILD, TEXT, HOST, "gabriel", "Valorant"))
                .isInstanceOf(UserFacingException.class).hasMessageContaining("LiveKit");
    }

    static class FakePresence implements VoicePresence {
        private final Map<String, Long> channels = new HashMap<>();

        void put(long guildId, long userId, long voiceChannelId) {
            channels.put(guildId + ":" + userId, voiceChannelId);
        }

        @Override
        public Optional<Long> voiceChannelOf(long guildId, long userId) {
            return Optional.ofNullable(channels.get(guildId + ":" + userId));
        }
    }

    static class FakeMedia implements MediaTokenIssuer {
        boolean configured = true;

        @Override
        public String token(String room, String identity, String displayName, boolean canPublish) {
            return "token:" + room + ":" + identity + ":" + displayName + ":" + canPublish;
        }

        @Override
        public String serverUrl() {
            return "wss://media.example";
        }

        @Override
        public boolean isConfigured() {
            return configured;
        }
    }
}
