package com.gabriellpa.sabadaco.discord.command.playlist;

import com.gabriellpa.sabadaco.discord.interaction.CommandHelp.Category;
import com.gabriellpa.sabadaco.discord.interaction.CommandHelp;
import com.gabriellpa.sabadaco.discord.interaction.Interactions;
import com.gabriellpa.sabadaco.discord.ui.PlaylistView;
import com.gabriellpa.sabadaco.playlist.PlaylistService;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.gabriellpa.sabadaco.discord.interaction.CommandHelp.example;

@Component
class ListPlaylists extends PlaylistSubcommand {

    ListPlaylists(PlaylistService playlists, PlaylistOptions options) {
        super(playlists, options);
    }

    @Override
    public SubcommandData definition() {
        return new SubcommandData("list", "Lists your playlists (this server and global)");
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        var visible = playlists.visibleTo(event.getUser().getIdLong(), Interactions.guildId(event));
        event.reply(PlaylistView.list(visible)).setEphemeral(true).queue();
    }

    @Override
    public CommandHelp help() {
        return CommandHelp.of(Category.PLAYLIST, "Lista suas playlists deste servidor e as globais.",
                List.of(example("/playlist list", "Mostra nome, escopo (🌐 global) e quantidade de músicas.")));
    }
}
