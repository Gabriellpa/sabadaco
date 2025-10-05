package com.gabriellpa.sabadaco.player;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
@RequiredArgsConstructor
public class PlayerManager {

    private final AudioPlayerManager playerManager;
    private final Map<Long, GuildMusicManager> musicManagers = new ConcurrentHashMap<>();

    public GuildMusicManager getGuildMusicManager(Long guildId) {
        return musicManagers.computeIfAbsent(guildId, id -> new GuildMusicManager(playerManager)
        );
    }
}
