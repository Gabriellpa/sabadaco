package com.gabriellpa.sabadaco.discord;

import com.gabriellpa.sabadaco.music.GuildPlayer;
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.audio.AudioSendHandler;

import java.nio.ByteBuffer;

/**
 * Ponte entre o Lavaplayer e o JDA: entrega um frame Opus a cada 20ms.
 */
@RequiredArgsConstructor
class AudioPlayerSendHandler implements AudioSendHandler {

    private final GuildPlayer player;
    private AudioFrame lastFrame;

    @Override
    public boolean canProvide() {
        lastFrame = player.provideFrame();
        return lastFrame != null;
    }

    @Override
    public ByteBuffer provide20MsAudio() {
        return ByteBuffer.wrap(lastFrame.getData());
    }

    @Override
    public boolean isOpus() {
        return true;
    }
}
