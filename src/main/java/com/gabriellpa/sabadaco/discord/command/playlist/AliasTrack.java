package com.gabriellpa.sabadaco.discord.command.playlist;

import com.gabriellpa.sabadaco.discord.interaction.CommandHelp.Category;
import com.gabriellpa.sabadaco.discord.interaction.CommandHelp;
import com.gabriellpa.sabadaco.discord.interaction.Interactions;
import com.gabriellpa.sabadaco.discord.ui.Format;
import com.gabriellpa.sabadaco.playlist.PlaylistService;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.gabriellpa.sabadaco.discord.interaction.CommandHelp.example;

@Component
class AliasTrack extends PlaylistSubcommand {

    AliasTrack(PlaylistService playlists, PlaylistOptions options) {
        super(playlists, options);
    }

    @Override
    public SubcommandData definition() {
        return new SubcommandData("alias", "Sets (or removes) a nickname for a playlist song")
                .addOptions(
                        PlaylistOptions.playlist(PlaylistOptions.PLAYLIST, "Playlist"),
                        PlaylistOptions.track(),
                        PlaylistOptions.alias(false).setDescription("New nickname (leave empty to remove)"));
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        var playlist = options.resolve(event, PlaylistOptions.PLAYLIST);
        var alias = Interactions.string(event, PlaylistOptions.ALIAS);
        var track = playlists.setAlias(event.getUser().getIdLong(), playlist.id(), PlaylistOptions.trackIndex(event), alias);
        event.reply(track.alias() == null
                        ? "🏷️ Apelido removido de **%s**.".formatted(Format.escape(track.title()))
                        : "🏷️ **%s** agora se chama **%s**.".formatted(Format.escape(track.title()), Format.escape(track.alias())))
                .setEphemeral(true).queue();
    }

    @Override
    public CommandHelp help() {
        return CommandHelp.of(Category.PLAYLIST, "Dá ou remove o apelido de uma música da playlist.",
                List.of(
                        example("/playlist alias Clássicos do Sabadaço 1 kassino", "A música 1 passa a se chamar \"kassino\"."),
                        example("/playlist alias Clássicos do Sabadaço 1", "Sem apelido: remove o apelido da música 1.")),
                "No campo da música, o autocomplete mostra as músicas pelo nome.");
    }
}
