package com.gabriellpa.sabadaco.discord.command.playlist;

import com.gabriellpa.sabadaco.discord.interaction.Interactions;
import com.gabriellpa.sabadaco.discord.ui.PlaylistView;
import com.gabriellpa.sabadaco.playlist.PlaylistService;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.springframework.stereotype.Component;

@Component
class ListPlaylists extends PlaylistSubcommand {

    ListPlaylists(PlaylistService playlists, PlaylistOptions options) {
        super(playlists, options);
    }

    @Override
    public SubcommandData definition() {
        return new SubcommandData("list", "Lista suas playlists (deste servidor e globais)");
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        var visible = playlists.visibleTo(event.getUser().getIdLong(), Interactions.guildId(event));
        event.reply(PlaylistView.list(visible)).setEphemeral(true).queue();
    }
}
