package com.gabriellpa.sabadaco.stream;

import com.gabriellpa.sabadaco.DiscordAdmins;
import com.gabriellpa.sabadaco.UserFacingException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
    private final FakeRooms rooms = new FakeRooms();
    private static final String STREAMS_CHANGED = "broadcast g10-c20 {\"type\":\"streams\"}";
    private final ViewerSessions sessions = new ViewerSessions(clock);
    private final StreamService service = new StreamService(StreamService.snowflakes(0),
            new InMemoryJoinCodeIssuer(clock, Duration.ofMinutes(5)), media, rooms, sessions, presence, DiscordAdmins.of(ADMIN),
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
    void activityJoinsTheStreamOfTheChannelWhereItWasOpened() {
        presence.put(GUILD, HOST, VOICE);
        presence.put(GUILD, HOST_2, VOICE);
        presence.put(GUILD, VIEWER, VOICE);
        service.create(GUILD, TEXT, HOST, "gabriel", "Valorant");
        service.create(GUILD, TEXT, HOST_2, "leão", "Minecraft");

        var viewer = service.joinFromDiscord(GUILD, VOICE, VIEWER, "viewer");
        assertThat(viewer.stream().title()).isEqualTo("Valorant");
        assertThat(viewer.canPublish()).isFalse();
        assertThat(viewer.session()).isNotBlank();
        assertThat(viewer.channelStreams()).hasSize(2);

        // Quem transmite cai na própria stream e pode compartilhar a tela
        var host = service.joinFromDiscord(GUILD, VOICE, HOST_2, "leão");
        assertThat(host.stream().title()).isEqualTo("Minecraft");
        assertThat(host.canPublish()).isTrue();

        // A sessão serve para o F5/reconectar como a do código
        assertThat(service.resume(viewer.session()).identity()).isEqualTo(VIEWER + "#atividade");
    }

    @Test
    void activityAndBrowserOfTheSamePersonHaveDifferentIdentitiesSoNeitherKicksTheOther() {
        presence.put(GUILD, HOST, VOICE);
        var stream = service.create(GUILD, TEXT, HOST, "gabriel", "Valorant").stream();

        var activity = service.joinFromDiscord(GUILD, VOICE, HOST, "gabriel");
        var browser = service.redeem(stream.id(), service.browserInvite(activity.session(), null).code().code());

        assertThat(activity.identity()).isEqualTo(HOST + "#atividade");
        assertThat(browser.identity()).isEqualTo(String.valueOf(HOST));
        assertThat(service.resume(activity.session()).identity()).isEqualTo(HOST + "#atividade");
        assertThat(service.resume(browser.session()).identity()).isEqualTo(String.valueOf(HOST));
        assertThat(activity.token()).isEqualTo("token:g10-c20:" + HOST + "#atividade:gabriel:true:" + stream.id());
    }

    @Test
    void activityNeedsThePersonInThatVoiceChannelButNotALiveStream() {
        presence.put(GUILD, HOST, VOICE);
        presence.put(GUILD, VIEWER, OTHER_VOICE);

        assertThatThrownBy(() -> service.joinFromDiscord(GUILD, VOICE, VIEWER, "viewer"))
                .isInstanceOf(UserFacingException.class).hasMessageContaining("canal de voz");

        // Canal sem stream: entra na sala vazia do canal, e a sessão sobrevive ao F5
        var empty = service.joinFromDiscord(GUILD, VOICE, HOST, "gabriel");
        assertThat(empty.stream()).isNull();
        assertThat(empty.room()).isEqualTo("g10-c20");
        assertThat(empty.canPublish()).isFalse();
        assertThat(empty.channelStreams()).isEmpty();
        assertThat(service.resume(empty.session()).stream()).isNull();
    }

    @Test
    void activityOpensAStreamInItsVoiceChannelAndLinksTheBrowserWithACode() {
        presence.put(GUILD, HOST, VOICE);
        var session = service.joinFromDiscord(GUILD, VOICE, HOST, "gabriel").session();

        var opened = service.openFromActivity(session, "Valorant");
        assertThat(opened.created()).isTrue();
        var stream = opened.invite().stream();
        assertThat(stream.voiceChannelId()).isEqualTo(VOICE);
        assertThat(stream.textChannelId()).isEqualTo(VOICE); // o chat fica no chat do próprio canal de voz
        assertThat(opened.invite().linkWithCode())
                .isEqualTo("https://sabadaco.example/stream/" + stream.id() + "#code=" + opened.invite().code().code());

        // Abrir de novo não cria outra: devolve a dela
        var again = service.openFromActivity(session, "Outra");
        assertThat(again.created()).isFalse();
        assertThat(again.invite().stream().id()).isEqualTo(stream.id());

        // O código do link vale no navegador (o mais novo substitui o anterior) e dá permissão de transmitir
        assertThat(service.redeem(stream.id(), again.invite().code().code()).canPublish()).isTrue();
    }

    @Test
    void activityBrowserLinkIsForTheChosenStreamOfThatChannel() {
        presence.put(GUILD, HOST, VOICE);
        presence.put(GUILD, HOST_2, VOICE);
        presence.put(GUILD, VIEWER, VOICE);
        var valorant = service.create(GUILD, TEXT, HOST, "gabriel", "Valorant").stream();
        var minecraft = service.create(GUILD, TEXT, HOST_2, "leão", "Minecraft").stream();
        var viewer = service.joinFromDiscord(GUILD, VOICE, VIEWER, "viewer").session();

        assertThat(service.browserInvite(viewer, null).stream().id()).isEqualTo(valorant.id());
        var chosen = service.browserInvite(viewer, minecraft.id());
        assertThat(chosen.stream().id()).isEqualTo(minecraft.id());
        assertThat(service.redeem(minecraft.id(), chosen.code().code()).identity()).isEqualTo(String.valueOf(VIEWER));
        assertThatThrownBy(() -> service.browserInvite(viewer, 123L))
                .isInstanceOf(UserFacingException.class).hasMessageContaining("terminou");

        // Quem saiu do canal de voz perde a sessão
        presence.put(GUILD, VIEWER, OTHER_VOICE);
        assertThatThrownBy(() -> service.browserInvite(viewer, null))
                .isInstanceOf(UserFacingException.class).hasMessageContaining("saiu do canal de voz");
        assertThatThrownBy(() -> service.resume(viewer)).isInstanceOf(UserFacingException.class);
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
        assertThat(ticket.token()).isEqualTo("token:g10-c20:2:viewer:false:");
        assertThat(ticket.serverUrl()).isEqualTo("wss://media.example");
    }

    @Test
    void hostCanPublish() {
        presence.put(GUILD, HOST, VOICE);
        var invite = service.create(GUILD, TEXT, HOST, "gabriel", "Valorant");

        var ticket = service.redeem(invite.stream().id(), invite.code().code());

        assertThat(ticket.canPublish()).isTrue();
        // o metadata diz à página de quem é cada tela: o id da stream de quem transmite
        assertThat(ticket.token()).endsWith(":true:" + invite.stream().id());
    }

    @Test
    void redeemOpensAPageSessionThatEndsWhenLeavingTheVoiceChannel() {
        presence.put(GUILD, HOST, VOICE);
        presence.put(GUILD, VIEWER, VOICE);
        var stream = service.create(GUILD, TEXT, HOST, "gabriel", "Valorant").stream();
        var ticket = service.redeem(stream.id(), service.join(GUILD, VIEWER, "viewer", "").code().code());

        var session = sessions.get(ticket.session()).orElseThrow();
        assertThat(session.streamId()).isEqualTo(stream.id());
        assertThat(session.userId()).isEqualTo(VIEWER);

        service.leftVoiceChannel(GUILD, VIEWER, OTHER_VOICE);
        assertThat(sessions.get(ticket.session())).isPresent();
        service.leftVoiceChannel(GUILD, VIEWER, VOICE);
        assertThat(sessions.get(ticket.session())).isEmpty();
    }

    @Test
    void resumeGivesANewTokenWithoutCodeWhileInTheVoiceChannel() {
        presence.put(GUILD, HOST, VOICE);
        presence.put(GUILD, VIEWER, VOICE);
        var stream = service.create(GUILD, TEXT, HOST, "gabriel", "Valorant").stream();
        var session = service.redeem(stream.id(), service.join(GUILD, VIEWER, "viewer", "").code().code()).session();

        var again = service.resume(session);
        assertThat(again.session()).isEqualTo(session);
        assertThat(again.canPublish()).isFalse();
        assertThat(service.channelStreams(session)).containsExactly(stream);

        // Abriu uma stream depois de entrar: o próximo token já pode transmitir
        var own = service.create(GUILD, TEXT, VIEWER, "viewer", "Minecraft").stream();
        var host = service.resume(session);
        assertThat(host.canPublish()).isTrue();
        assertThat(host.token()).endsWith(":true:" + own.id());

        presence.put(GUILD, VIEWER, OTHER_VOICE);
        assertThatThrownBy(() -> service.resume(session))
                .isInstanceOf(UserFacingException.class).hasMessageContaining("saiu do canal de voz");
        assertThatThrownBy(() -> service.channelStreams(session))
                .isInstanceOf(UserFacingException.class).hasMessageContaining("sessão terminou");
    }

    @Test
    void resumeFallsBackToAnotherLiveStreamOfTheChannel() {
        presence.put(GUILD, HOST, VOICE);
        presence.put(GUILD, HOST_2, VOICE);
        presence.put(GUILD, VIEWER, VOICE);
        var valorant = service.create(GUILD, TEXT, HOST, "gabriel", "Valorant").stream();
        var session = service.redeem(valorant.id(), service.join(GUILD, VIEWER, "viewer", "").code().code()).session();
        service.end(valorant.id(), HOST);

        assertThatThrownBy(() -> service.resume(session))
                .isInstanceOf(UserFacingException.class).hasMessageContaining("Não há stream ao vivo");

        var minecraft = service.create(GUILD, TEXT, HOST_2, "leão", "Minecraft").stream();
        assertThat(service.resume(session).stream()).isEqualTo(minecraft);
    }

    @Test
    void codeOnlyWorksOnItsOwnStreamLinkButShowsEveryStreamOfTheChannel() {
        presence.put(GUILD, HOST, VOICE);
        presence.put(GUILD, HOST_2, VOICE);
        presence.put(GUILD, VIEWER, VOICE);
        var valorant = service.create(GUILD, TEXT, HOST, "gabriel", "Valorant").stream();
        var minecraft = service.create(GUILD, TEXT, HOST_2, "leão", "Minecraft").stream();
        var valorantCode = service.join(GUILD, VIEWER, "viewer", "Valorant").code().code();

        assertThatThrownBy(() -> service.redeem(minecraft.id(), valorantCode))
                .isInstanceOf(UserFacingException.class).hasMessageContaining("outra stream");

        var otherCode = service.join(GUILD, VIEWER, "viewer", "Valorant").code().code();
        var ticket = service.redeem(valorant.id(), otherCode);

        assertThat(ticket.channelStreams()).extracting(StreamSession::title).containsExactly("Valorant", "Minecraft");
        assertThatThrownBy(() -> service.redeem(valorant.id(), otherCode))
                .isInstanceOf(UserFacingException.class).hasMessageContaining("inválido ou expirado");
    }

    @Test
    void codeFromAnotherChannelDoesNotOpenTheStream() {
        presence.put(GUILD, HOST, VOICE);
        presence.put(GUILD, HOST_2, OTHER_VOICE);
        var valorant = service.create(GUILD, TEXT, HOST, "gabriel", "Valorant").stream();
        var otherChannelCode = service.create(GUILD, TEXT, HOST_2, "leão", "Minecraft").code().code();

        assertThatThrownBy(() -> service.redeem(valorant.id(), otherChannelCode))
                .isInstanceOf(UserFacingException.class).hasMessageContaining("outra stream");
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
    void onlyHostOrAdminEndsAndTheHostStopsPublishing() {
        presence.put(GUILD, HOST, VOICE);
        var stream = service.create(GUILD, TEXT, HOST, "gabriel", "Valorant").stream();

        assertThatThrownBy(() -> service.end(stream.id(), VIEWER)).isInstanceOf(UserFacingException.class);
        service.end(stream.id(), ADMIN);

        assertThat(service.get(stream.id())).isEmpty();
        // Abrir e encerrar avisam a sala, e a página atualiza a lista na hora
        assertThat(rooms.calls).containsExactly(STREAMS_CHANGED, "revokePublish g10-c20 1",
                "revokePublish g10-c20 1#atividade", STREAMS_CHANGED);
    }

    @Test
    void endOwnEndsTheCallersStream() {
        presence.put(GUILD, HOST, VOICE);
        var stream = service.create(GUILD, TEXT, HOST, "gabriel", "Valorant").stream();

        assertThat(service.endOwn(GUILD, HOST)).isEqualTo(stream);
        assertThatThrownBy(() -> service.endOwn(GUILD, HOST))
                .isInstanceOf(UserFacingException.class).hasMessageContaining("não tem nenhuma stream");
    }

    @Test
    void leavingTheVoiceChannelEndsHostedStreamRevokesCodesAndLeavesTheRoom() {
        presence.put(GUILD, HOST, VOICE);
        presence.put(GUILD, VIEWER, VOICE);
        var stream = service.create(GUILD, TEXT, HOST, "gabriel", "Valorant").stream();
        var viewerCode = service.join(GUILD, VIEWER, "viewer", "").code().code();

        assertThat(service.leftVoiceChannel(GUILD, VIEWER, VOICE)).isEmpty();
        assertThatThrownBy(() -> service.redeem(stream.id(), viewerCode)).isInstanceOf(UserFacingException.class);

        assertThat(service.leftVoiceChannel(GUILD, HOST, VOICE)).containsExactly(stream);
        assertThat(service.streamsIn(GUILD, VOICE)).isEmpty();
        assertThat(rooms.calls).containsExactly(STREAMS_CHANGED, "remove g10-c20 2", "remove g10-c20 2#atividade",
                "remove g10-c20 1", "remove g10-c20 1#atividade", STREAMS_CHANGED);
    }

    @Test
    void leavingAnotherChannelKeepsTheStream() {
        presence.put(GUILD, HOST, VOICE);
        var stream = service.create(GUILD, TEXT, HOST, "gabriel", "Valorant").stream();

        assertThat(service.leftVoiceChannel(GUILD, HOST, OTHER_VOICE)).isEmpty();
        assertThat(service.get(stream.id())).isPresent();
    }

    @Test
    void joinStreamFromTheCardButtonRequiresItsVoiceChannel() {
        presence.put(GUILD, HOST, VOICE);
        presence.put(GUILD, VIEWER, OTHER_VOICE);
        var stream = service.create(GUILD, TEXT, HOST, "gabriel", "Valorant").stream();

        assertThatThrownBy(() -> service.joinStream(GUILD, VIEWER, "viewer", stream.id()))
                .isInstanceOf(UserFacingException.class).hasMessageContaining("Entre no canal de voz da stream");

        presence.put(GUILD, VIEWER, VOICE);
        var invite = service.joinStream(GUILD, VIEWER, "viewer", stream.id());
        assertThat(service.redeem(stream.id(), invite.code().code()).identity()).isEqualTo(String.valueOf(VIEWER));

        service.end(stream.id(), HOST);
        assertThatThrownBy(() -> service.joinStream(GUILD, VIEWER, "viewer", stream.id()))
                .isInstanceOf(UserFacingException.class).hasMessageContaining("já terminou");
    }

    @Test
    void joinFindsByStreamIdFromAutocomplete() {
        presence.put(GUILD, HOST, VOICE);
        presence.put(GUILD, HOST_2, VOICE);
        presence.put(GUILD, VIEWER, VOICE);
        service.create(GUILD, TEXT, HOST, "gabriel", "Valorant");
        var minecraft = service.create(GUILD, TEXT, HOST_2, "leão", "Minecraft").stream();

        assertThat(service.join(GUILD, VIEWER, "viewer", String.valueOf(minecraft.id())).stream()).isEqualTo(minecraft);
    }

    @Test
    void snowflakeIdsAreUniqueIncreasingAndCarryTheCreationTime() {
        var ids = StreamService.snowflakes(3);
        long previous = -1;
        for (int i = 0; i < 10_000; i++) { // mais que as 4096 sequências de um milissegundo
            long id = ids.getAsLong();
            assertThat(id).isGreaterThan(previous);
            previous = id;
        }
        var createdAt = StreamService.ID_EPOCH.plusMillis(previous >>> 22);
        assertThat(createdAt).isBetween(Instant.now().minusSeconds(60), Instant.now().plusSeconds(1));
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

    static class FakeRooms implements MediaRooms {
        final List<String> calls = new ArrayList<>();

        @Override
        public void remove(String room, String identity) {
            calls.add("remove " + room + " " + identity);
        }

        @Override
        public void revokePublish(String room, String identity) {
            calls.add("revokePublish " + room + " " + identity);
        }

        @Override
        public void broadcast(String room, byte[] data) {
            calls.add("broadcast " + room + " " + new String(data, java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    static class FakeMedia implements MediaTokenIssuer {
        boolean configured = true;

        @Override
        public String token(String room, String identity, String displayName, boolean canPublish, String metadata) {
            return "token:" + room + ":" + identity + ":" + displayName + ":" + canPublish + ":" + metadata;
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
