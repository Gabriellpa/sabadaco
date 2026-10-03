package com.gabriellpa.sabadaco.music;

import com.gabriellpa.sabadaco.UserFacingException;
import com.gabriellpa.sabadaco.music.event.PlayerEvent;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TrackSchedulerTest {

    private final AudioPlayer player = mock(AudioPlayer.class);
    private final List<PlayerEvent> events = new ArrayList<>();
    private final List<String> started = new ArrayList<>();
    private TrackScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new TrackScheduler(1L, player, events::add);
        when(player.startTrack(any(), anyBoolean())).thenAnswer(invocation -> {
            AudioTrack track = invocation.getArgument(0);
            started.add(track.getInfo().title);
            return true;
        });
    }

    @Test
    void firstTrackStartsImmediately() {
        var result = scheduler.enqueue(List.of(single("A")));

        assertThat(result.startedNow()).isTrue();
        assertThat(started).containsExactly("A");
    }

    @Test
    void singleTrackJumpsAheadOfRemainingPlaylistAndReportsIt() {
        scheduler.enqueue(List.of(fromPlaylist("P1", "rock"), fromPlaylist("P2", "rock"), fromPlaylist("P3", "rock")));

        var result = scheduler.enqueue(List.of(single("S")));

        assertThat(result.position()).isEqualTo(1);
        assertThat(result.skippedPlaylistTracks()).isEqualTo(2);
        assertThat(result.skippedPlaylistName()).isEqualTo("rock");
        assertThat(titles(scheduler.snapshot().upcoming())).containsExactly("S", "P2", "P3");
    }

    @Test
    void playlistAddedAfterSinglesReportsSinglesAhead() {
        scheduler.enqueue(List.of(single("A")));
        scheduler.enqueue(List.of(single("B")));

        var result = scheduler.enqueue(List.of(fromPlaylist("P1", "mix"), fromPlaylist("P2", "mix")));

        assertThat(result.singlesAhead()).isEqualTo(1);
        assertThat(result.position()).isEqualTo(2);
    }

    @Test
    void nextPlaysPriorityLaneBeforePlaylistLane() {
        scheduler.enqueue(List.of(fromPlaylist("P1", "mix"), fromPlaylist("P2", "mix")));
        scheduler.enqueue(List.of(single("S")));

        scheduler.next();
        scheduler.next();

        assertThat(started).containsExactly("P1", "S", "P2");
    }

    @Test
    void trackLoopRepeatsOnNaturalEndButSkipMovesOn() {
        scheduler.enqueue(List.of(single("A"), single("B")));
        scheduler.cycleLoop(); // TRACK

        scheduler.next();
        scheduler.skip();

        assertThat(started).containsExactly("A", "A", "B");
    }

    @Test
    void queueLoopRequeuesFinishedTrack() {
        scheduler.enqueue(List.of(single("A"), single("B")));
        scheduler.cycleLoop();
        scheduler.cycleLoop(); // QUEUE

        scheduler.next();
        scheduler.next();

        assertThat(started).containsExactly("A", "B", "A");
    }

    @Test
    void removeAndMoveUseExecutionOrderAndRespectLanes() {
        scheduler.enqueue(List.of(single("Now")));
        scheduler.enqueue(List.of(single("S1"), single("S2")));
        scheduler.enqueue(List.of(fromPlaylist("P1", "mix")));

        scheduler.move(1, 0);
        assertThat(titles(scheduler.snapshot().upcoming())).containsExactly("S2", "S1", "P1");

        assertThat(scheduler.remove(2).track().title()).isEqualTo("P1");
        assertThatThrownBy(() -> scheduler.remove(5)).isInstanceOf(UserFacingException.class);
    }

    @Test
    void movingBetweenLanesIsRejected() {
        scheduler.enqueue(List.of(single("Now")));
        scheduler.enqueue(List.of(single("S1")));
        scheduler.enqueue(List.of(fromPlaylist("P1", "mix")));

        assertThatThrownBy(() -> scheduler.move(1, 0)).isInstanceOf(UserFacingException.class);
    }

    @Test
    void trackEndCountsBytesAndAdvancesAsynchronously() {
        var first = single("A");
        scheduler.enqueue(List.of(first, single("B")));
        scheduler.recordSentBytes(1000);
        assertThat(scheduler.snapshot().currentTrackBytes()).isEqualTo(1000L);

        scheduler.onTrackEnd(player, first.track(), AudioTrackEndReason.FINISHED);

        var ended = events.stream().filter(PlayerEvent.TrackEnded.class::isInstance).map(PlayerEvent.TrackEnded.class::cast).findFirst();
        assertThat(ended).get().extracting(PlayerEvent.TrackEnded::bytesSent).isEqualTo(1000L);
        assertThat(scheduler.totalBytesSent()).isEqualTo(1000L);
        await().untilAsserted(() -> assertThat(started).containsExactly("A", "B"));
    }

    @Test
    void lateEndOfAnAlreadySkippedTrackDoesNotSkipAgain() {
        var first = single("A");
        scheduler.enqueue(List.of(first, single("B"), single("C")));
        scheduler.skip(); // usuário pulou: B tocando

        scheduler.advanceAfter(first.track()); // fim atrasado de A não deve pular B

        assertThat(started).containsExactly("A", "B");
    }

    @Test
    void stopClearsEverything() {
        scheduler.enqueue(List.of(single("A"), single("B")));

        scheduler.stop();

        var snapshot = scheduler.snapshot();
        assertThat(snapshot.playing()).isFalse();
        assertThat(snapshot.queueSize()).isZero();
        assertThat(events).anyMatch(PlayerEvent.Stopped.class::isInstance);
    }

    private static List<String> titles(List<QueueEntry> entries) {
        return entries.stream().map(entry -> entry.track().title()).toList();
    }

    private static QueuedTrack single(String title) {
        return QueuedTrack.single(track(title), 42L);
    }

    private static QueuedTrack fromPlaylist(String title, String playlist) {
        return QueuedTrack.fromPlaylist(track(title), 42L, playlist);
    }

    static AudioTrack track(String title) {
        var track = mock(AudioTrack.class);
        when(track.getInfo()).thenReturn(info(title));
        when(track.makeClone()).thenAnswer(invocation -> track(title));
        return track;
    }

    private static AudioTrackInfo info(String title) {
        return new AudioTrackInfo(title, "author", 180_000, title, false, "https://example.com/" + title);
    }
}
