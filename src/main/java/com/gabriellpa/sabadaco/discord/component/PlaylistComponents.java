package com.gabriellpa.sabadaco.discord.component;

import com.gabriellpa.sabadaco.UserFacingException;
import com.gabriellpa.sabadaco.discord.command.PlaybackReplies;
import com.gabriellpa.sabadaco.discord.interaction.ComponentHandler;
import com.gabriellpa.sabadaco.discord.interaction.Interactions;
import com.gabriellpa.sabadaco.discord.ui.Format;
import com.gabriellpa.sabadaco.discord.ui.PlaylistView;
import com.gabriellpa.sabadaco.playlist.PlaylistService;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.springframework.stereotype.Component;

/** Botões de playlist: tocar, tocar aleatório e confirmar exclusão. */
@Component
@RequiredArgsConstructor
public class PlaylistComponents implements ComponentHandler {

    private final PlaybackReplies playback;
    private final PlaylistService playlistService;

    @Override
    public String prefix() {
        return PlaylistView.PREFIX;
    }

    @Override
    public void onButton(ButtonInteractionEvent event, String action, String payload) {
        long userId = event.getUser().getIdLong();
        switch (action) {
            case "play" -> playback.playPlaylist(event, playlistService.playable(userId, Interactions.guildId(event), payload), false);
            case "shuffle" -> playback.playPlaylist(event, playlistService.playable(userId, Interactions.guildId(event), payload), true);
            case "delete" -> {
                var playlist = playlistService.owned(userId, payload);
                playlistService.delete(userId, payload);
                event.editMessage("🗑️ Playlist **" + Format.escape(playlist.name()) + "** apagada.").setComponents().queue();
            }
            case "cancel" -> event.editMessage("Ok, nada foi apagado.").setComponents().queue();
            default -> throw new UserFacingException("Ação desconhecida: " + action);
        }
    }
}
