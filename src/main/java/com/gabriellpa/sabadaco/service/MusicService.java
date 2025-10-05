package com.gabriellpa.sabadaco.service;

import com.gabriellpa.sabadaco.player.AudioLoadHandler;
import com.gabriellpa.sabadaco.player.GuildMusicManager;
import com.gabriellpa.sabadaco.player.PlayerManager;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MusicService {

    private final PlayerManager playerManager;
    private final AudioPlayerManager audioPlayerManager;

    public void play(Guild guild, AudioChannel voiceChannel, TextChannel textChannel, String trackUrl) {
        GuildMusicManager musicManager = playerManager.getGuildMusicManager(guild.getIdLong());

        // conecta no canal de voz se não estiver
        if (!guild.getAudioManager().isConnected()) {
            guild.getAudioManager().openAudioConnection(voiceChannel);
            guild.getAudioManager().setSendingHandler(musicManager.getSendHandler());
        }

        // carrega e enfileira a música
        audioPlayerManager.loadItemOrdered(
                musicManager,
                handleUrl(trackUrl),
                new AudioLoadHandler(musicManager, textChannel)
        );
    }

    private String handleUrl(String url) {
        if (url.contains("youtube")) {
            return url.replace("https://", "");
        }
        return url;
    }
}
