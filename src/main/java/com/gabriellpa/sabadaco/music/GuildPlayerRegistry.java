package com.gabriellpa.sabadaco.music;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class GuildPlayerRegistry {

    private final AudioPlayerManager audioPlayerManager;
    private final ApplicationEventPublisher events;
    private final Map<Long, GuildPlayer> players = new ConcurrentHashMap<>();

    public GuildPlayer getOrCreate(long guildId) {
        return players.computeIfAbsent(guildId, id -> new GuildPlayer(id, audioPlayerManager, events::publishEvent));
    }

    public Optional<GuildPlayer> find(long guildId) {
        return Optional.ofNullable(players.get(guildId));
    }

    public Collection<GuildPlayer> all() {
        return players.values();
    }
}
