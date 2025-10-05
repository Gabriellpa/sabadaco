package com.gabriellpa.sabadaco.player;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import lombok.Getter;

public class GuildMusicManager {

    @Getter
    public final AudioPlayer player;
    @Getter
    public final TrackScheduler scheduler;

    public GuildMusicManager(AudioPlayerManager manager) {
        this.player = manager.createPlayer();
        this.scheduler = new TrackScheduler(this.player);
        this.player.addListener(this.scheduler);
    }

    public void addTrack(AudioTrack track){
        scheduler.queue(track);
    }

    public AudioPlayerSendHandler getSendHandler() {
        return new AudioPlayerSendHandler(player);
    }

}
