package com.gabriellpa.sabadaco.player.config;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import dev.lavalink.youtube.YoutubeSourceOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static dev.lavalink.youtube.YoutubeAudioSourceManager.DEFAULT_CLIENTS;

@Configuration
public class PlayerConfiguration {

    @Bean
    @SuppressWarnings("deprecation")
    public AudioPlayerManager AudioPlayerManager() {
        AudioPlayerManager playerManager = new DefaultAudioPlayerManager();
        YoutubeSourceOptions options = new YoutubeSourceOptions()
                .setAllowSearch(true)
                .setAllowDirectVideoIds(true)
                .setAllowDirectPlaylistIds(true)
                //TODO: Hospedar o próprio servidor de chiper https://github.com/kikkia/yt-cipher o servidor usado hoje é publico e não é garantido uptime nem performance
                .setRemoteCipherUrl("https://cipher.kikkia.dev/api", null);
        // Youtube source precisa ser o primeiro para evitar conflito com HttpAudioSourceManager
        playerManager.registerSourceManager(new YoutubeAudioSourceManager(options, DEFAULT_CLIENTS));
        //TODO: criar um override para HttpAudioSourceManager e deixar a verificação mais robusta para não conflitar com outras sources
        AudioSourceManagers.registerRemoteSources(playerManager, com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioSourceManager.class);
        return playerManager;
    }
}
