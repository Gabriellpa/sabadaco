package com.gabriellpa.sabadaco.admin;

import com.gabriellpa.sabadaco.UserFacingException;
import com.gabriellpa.sabadaco.music.MusicService;

/** Ações de player compartilhadas pelas telas do admin. */
final class PlayerControls {

    private PlayerControls() {
    }

    static void apply(MusicService musicService, long guildId, String action) {
        switch (action) {
            case "pause" -> musicService.togglePause(guildId);
            case "skip" -> musicService.skip(guildId);
            case "stop" -> musicService.stop(guildId);
            case "shuffle" -> musicService.shuffle(guildId);
            case "loop" -> musicService.cycleLoop(guildId);
            case "clear" -> musicService.clearQueue(guildId);
            default -> throw new UserFacingException("Ação desconhecida: " + action);
        }
    }
}
