package com.gabriellpa.sabadaco.discord.command.playlist;

import com.gabriellpa.sabadaco.discord.interaction.CommandHelp.Category;
import com.gabriellpa.sabadaco.discord.interaction.CommandHelp;
import com.gabriellpa.sabadaco.discord.ui.Format;
import com.gabriellpa.sabadaco.playlist.PlaylistService;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.gabriellpa.sabadaco.discord.interaction.CommandHelp.example;

/** "Mova a música A da playlist 1 para a playlist 2". */
@Component
class MoveTrack extends PlaylistSubcommand {

    MoveTrack(PlaylistService playlists, PlaylistOptions options) {
        super(playlists, options);
    }

    @Override
    public SubcommandData definition() {
        return new SubcommandData("move", "Moves a song from one playlist to another")
                .addOptions(
                        PlaylistOptions.playlist(PlaylistOptions.FROM, "Source playlist"),
                        PlaylistOptions.track(),
                        PlaylistOptions.playlist(PlaylistOptions.TO, "Target playlist"));
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        var from = options.resolve(event, PlaylistOptions.FROM);
        var to = options.resolve(event, PlaylistOptions.TO);
        var moved = playlists.moveTrack(event.getUser().getIdLong(), from.id(), PlaylistOptions.trackIndex(event), to.id());
        event.reply("↪️ **%s** movida de **%s** para **%s**.".formatted(
                        Format.escape(moved.displayName()), Format.escape(from.name()), Format.escape(to.name())))
                .setEphemeral(true).queue();
    }

    @Override
    public CommandHelp help() {
        return CommandHelp.of(Category.PLAYLIST, "Move uma música de uma playlist para outra.",
                List.of(example("/playlist move Clássicos do Sabadaço 1 Favoritas", "Move a música 1 (com o apelido) para Favoritas.")),
                "Escolha a playlist de origem primeiro: o autocomplete da música usa ela.");
    }
}
