package com.gabriellpa.sabadaco.playlist;

import com.gabriellpa.sabadaco.DiscordAdmins;
import com.gabriellpa.sabadaco.UserFacingException;
import com.gabriellpa.sabadaco.music.TrackSummary;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlaylistServiceTest {

    private static final long ALICE = 1;
    private static final long BOB = 2;
    private static final long ADMIN = 3;
    private static final long GUILD_A = 100;
    private static final long GUILD_B = 200;

    private final PlaylistService service = new PlaylistService(new InMemoryPlaylistRepository(), DiscordAdmins.of(ADMIN));

    @Test
    void defaultsToGuildScope() {
        var playlist = service.create(ALICE, GUILD_A, "Rock", null);

        assertThat(playlist.scope()).isEqualTo(PlaylistScope.GUILD);
        assertThat(playlist.guildId()).isEqualTo(GUILD_A);
    }

    @Test
    void visibilityIsGuildPlaylistsPlusOwnGlobals() {
        service.create(ALICE, GUILD_A, "Rock A", PlaylistScope.GUILD);
        service.create(ALICE, GUILD_B, "Rock B", PlaylistScope.GUILD);
        service.create(ALICE, GUILD_A, "Favoritas", PlaylistScope.GLOBAL);
        service.create(BOB, GUILD_A, "Do Bob", PlaylistScope.GUILD);

        assertThat(service.visibleTo(ALICE, GUILD_B)).extracting(Playlist::name).containsExactly("Rock B", "Favoritas");
    }

    @Test
    void sameNameAllowedInDifferentScopesButNotTwiceInTheSame() {
        service.create(ALICE, GUILD_A, "Mix", PlaylistScope.GUILD);
        service.create(ALICE, GUILD_A, "Mix", PlaylistScope.GLOBAL);

        assertThatThrownBy(() -> service.create(ALICE, GUILD_A, "mix", PlaylistScope.GUILD))
                .isInstanceOf(UserFacingException.class);
    }

    @Test
    void movesTrackWithAliasBetweenPlaylists() {
        var from = service.create(ALICE, GUILD_A, "Um", null);
        var to = service.create(ALICE, GUILD_A, "Dois", PlaylistScope.GLOBAL);
        service.addTrack(ALICE, from.id(), track("Abertura"), "kassino");
        service.addTrack(ALICE, from.id(), track("Outra"), null);

        var moved = service.moveTrack(ALICE, from.id(), 0, to.id());

        assertThat(moved.alias()).isEqualTo("kassino");
        assertThat(service.get(from.id()).tracks()).extracting(PlaylistTrack::title).containsExactly("Outra");
        assertThat(service.get(to.id()).tracks()).extracting(PlaylistTrack::alias).containsExactly("kassino");
    }

    @Test
    void aliasCanBeSetAndRemoved() {
        var playlist = service.create(ALICE, GUILD_A, "Um", null);
        service.addTrack(ALICE, playlist.id(), track("Abertura"), null);

        assertThat(service.setAlias(ALICE, playlist.id(), 0, "abertura").alias()).isEqualTo("abertura");
        assertThat(service.aliasedTracks(ALICE, GUILD_A)).hasSize(1);
        assertThat(service.setAlias(ALICE, playlist.id(), 0, " ").alias()).isNull();
    }

    @Test
    void onlyOwnerCanChangeAPlaylist() {
        var playlist = service.create(ALICE, GUILD_A, "Um", null);

        assertThatThrownBy(() -> service.addTrack(BOB, playlist.id(), track("x"), null))
                .isInstanceOf(UserFacingException.class)
                .hasMessageContaining("não é sua");
    }

    @Test
    void invalidTrackIndexIsAFriendlyError() {
        var playlist = service.create(ALICE, GUILD_A, "Um", null);

        assertThatThrownBy(() -> service.removeTrack(ALICE, playlist.id(), 3))
                .isInstanceOf(UserFacingException.class)
                .hasMessageContaining("#4");
    }

    private static TrackSummary track(String title) {
        return new TrackSummary(title, "autor", "https://youtu.be/" + title, 120_000, null, false);
    }

    @Test
    void adminBrowsesAndPlaysEveryonesPlaylistsButOnlyEditsOwn() {
        var mine = service.create(ADMIN, GUILD_A, "Rock", null);
        var bobs = service.create(BOB, GUILD_B, "Rock", null);
        service.create(ALICE, GUILD_A, "Pagode", PlaylistScope.GLOBAL);

        assertThat(service.browsableBy(ADMIN, GUILD_A)).extracting(Playlist::id).startsWith(mine.id()).hasSize(3);
        assertThat(service.playable(ADMIN, bobs.id())).isEqualTo(bobs);
        assertThatThrownBy(() -> service.rename(ADMIN, bobs.id(), "Meu agora")).isInstanceOf(UserFacingException.class);
    }

    @Test
    void regularUserOnlyBrowsesAndPlaysOwn() {
        service.create(ALICE, GUILD_A, "Pagode", null);
        var bobs = service.create(BOB, GUILD_A, "Rock", null);

        assertThat(service.browsableBy(ALICE, GUILD_A)).extracting(Playlist::name).containsExactly("Pagode");
        assertThatThrownBy(() -> service.playable(ALICE, bobs.id())).isInstanceOf(UserFacingException.class);
    }
}
