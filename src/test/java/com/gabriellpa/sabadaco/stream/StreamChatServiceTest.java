package com.gabriellpa.sabadaco.stream;

import com.gabriellpa.sabadaco.DiscordAdmins;
import com.gabriellpa.sabadaco.UserFacingException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StreamChatServiceTest {

    private static final long GUILD = 10;
    private static final long VOICE = 20;
    private static final long OTHER_VOICE = 21;
    private static final long TEXT = 30;
    private static final long OTHER_TEXT = 31;
    private static final long HOST = 1;
    private static final long VIEWER = 2;
    private static final long HOST_2 = 4;

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-10T20:00:00Z"));
    private final StreamServiceTest.FakePresence presence = new StreamServiceTest.FakePresence();
    private final StreamServiceTest.FakeRooms rooms = new StreamServiceTest.FakeRooms();
    private final ViewerSessions sessions = new ViewerSessions(clock);
    private final StreamService streams = new StreamService(StreamService.snowflakes(0),
            new InMemoryJoinCodeIssuer(clock, Duration.ofMinutes(5)), new StreamServiceTest.FakeMedia(), rooms, sessions,
            presence, DiscordAdmins.of(), clock, "https://sabadaco.example");
    private final FakeRelay relay = new FakeRelay();
    private final StreamChatService chat = new StreamChatService(streams, sessions, presence, relay, rooms,
            JsonMapper.builder().build(), clock);

    @Test
    void webMessageGoesToTheStreamsTextChannelAndToTheRoom() {
        var session = viewerSession();

        chat.fromWeb(session, "  boa jogada  ");

        assertThat(relay.posts).containsExactly("10/30 2 viewer: boa jogada");
        assertThat(rooms.calls).singleElement().asString()
                .startsWith("broadcast g10-c20 {")
                .contains("\"type\":\"chat\"", "\"author\":\"viewer\"", "\"content\":\"boa jogada\"", "\"fromWeb\":true",
                        "\"avatarUrl\":\"https://cdn.discordapp.com/avatars/2.png\"");
    }

    @Test
    void webMessageRules() {
        var session = viewerSession();

        assertThatThrownBy(() -> chat.fromWeb("inventada", "oi")).isInstanceOf(UserFacingException.class)
                .hasMessageContaining("sessão terminou");
        assertThatThrownBy(() -> chat.fromWeb(session, "   ")).isInstanceOf(UserFacingException.class);
        assertThatThrownBy(() -> chat.fromWeb(session, "a".repeat(501))).isInstanceOf(UserFacingException.class);

        chat.fromWeb(session, "um");
        assertThatThrownBy(() -> chat.fromWeb(session, "dois")).isInstanceOf(UserFacingException.class)
                .hasMessageContaining("uma mensagem de cada vez");
        clock.advance(Duration.ofSeconds(1));
        chat.fromWeb(session, "dois");

        presence.put(GUILD, VIEWER, OTHER_VOICE);
        assertThatThrownBy(() -> chat.fromWeb(session, "três")).isInstanceOf(UserFacingException.class)
                .hasMessageContaining("saiu do canal de voz");
        assertThat(relay.posts).hasSize(2);
    }

    @Test
    void discordMessageGoesOnlyToRoomsOfStreamsCreatedInThatTextChannel() {
        presence.put(GUILD, HOST, VOICE);
        presence.put(GUILD, HOST_2, OTHER_VOICE);
        streams.create(GUILD, TEXT, HOST, "gabriel", "Valorant");
        streams.create(GUILD, OTHER_TEXT, HOST_2, "leão", "Minecraft");

        assertThat(chat.mirrors(GUILD, TEXT)).isTrue();
        assertThat(chat.fromDiscord(GUILD, TEXT, "99", "ana", null, "gg", clock.instant())).isTrue();
        assertThat(chat.fromDiscord(GUILD, TEXT, "100", "ana", null, " ", clock.instant())).isFalse();
        assertThat(chat.fromDiscord(GUILD, 777, "101", "ana", null, "oi", clock.instant())).isFalse();
        assertThat(chat.mirrors(GUILD, 777)).isFalse();

        assertThat(rooms.calls).singleElement().asString()
                .startsWith("broadcast g10-c20 ")
                .contains("\"id\":\"99\"", "\"content\":\"gg\"", "\"fromWeb\":false");
    }

    @Test
    void afterTheEntryStreamEndsChatGoesToAnotherLiveStreamOfTheChannel() {
        presence.put(GUILD, HOST, VOICE);
        presence.put(GUILD, HOST_2, VOICE);
        presence.put(GUILD, VIEWER, VOICE);
        var first = streams.create(GUILD, TEXT, HOST, "gabriel", "Valorant").stream();
        streams.create(GUILD, OTHER_TEXT, HOST_2, "leão", "Minecraft");
        var session = streams.redeem(first.id(), streams.join(GUILD, VIEWER, "viewer", "Valorant").code().code()).session();
        streams.end(first.id(), HOST);

        chat.fromWeb(session, "e agora?");

        assertThat(relay.posts).containsExactly("10/31 2 viewer: e agora?");
    }

    private String viewerSession() {
        presence.put(GUILD, HOST, VOICE);
        presence.put(GUILD, VIEWER, VOICE);
        var stream = streams.create(GUILD, TEXT, HOST, "gabriel", "Valorant").stream();
        return streams.redeem(stream.id(), streams.join(GUILD, VIEWER, "viewer", "").code().code()).session();
    }

    static class FakeRelay implements ChatRelay {
        final List<String> posts = new ArrayList<>();

        @Override
        public void postToDiscord(long guildId, long textChannelId, long userId, String displayName, String content) {
            posts.add(guildId + "/" + textChannelId + " " + userId + " " + displayName + ": " + content);
        }

        @Override
        public String avatarUrl(long guildId, long userId) {
            return "https://cdn.discordapp.com/avatars/" + userId + ".png";
        }
    }
}
