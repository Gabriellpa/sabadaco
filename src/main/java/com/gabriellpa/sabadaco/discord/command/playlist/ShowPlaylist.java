package com.gabriellpa.sabadaco.discord.command.playlist;

import com.gabriellpa.sabadaco.discord.interaction.CommandHelp.Category;
import com.gabriellpa.sabadaco.discord.interaction.CommandHelp;
import com.gabriellpa.sabadaco.discord.ui.Messages;
import com.gabriellpa.sabadaco.discord.ui.PlaylistView;
import com.gabriellpa.sabadaco.playlist.PlaylistService;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.gabriellpa.sabadaco.discord.interaction.CommandHelp.example;

@Component
class ShowPlaylist extends PlaylistSubcommand {

    ShowPlaylist(PlaylistService playlists, PlaylistOptions options) {
        super(playlists, options);
    }

    @Override
    public SubcommandData definition() {
        return new SubcommandData("show", "Shows the songs in a playlist")
                .addOptions(PlaylistOptions.playlist(PlaylistOptions.PLAYLIST, "Playlist"));
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        var playlist = options.resolve(event, PlaylistOptions.PLAYLIST);
        event.reply(Messages.create(PlaylistView.render(playlist))).setEphemeral(true).queue();
    }

    @Override
    public CommandHelp help() {
        return CommandHelp.of(Category.PLAYLIST, "Mostra as músicas de uma playlist.",
                List.of(example("/playlist show Clássicos do Sabadaço", "Lista as músicas com apelidos e traz os botões ▶️ Tocar e 🔀 Aleatório.")));
    }
}
