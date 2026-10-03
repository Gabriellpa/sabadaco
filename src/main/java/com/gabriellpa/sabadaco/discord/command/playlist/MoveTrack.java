package com.gabriellpa.sabadaco.discord.command.playlist;

import com.gabriellpa.sabadaco.discord.ui.Format;
import com.gabriellpa.sabadaco.playlist.PlaylistService;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.springframework.stereotype.Component;

/** "Mova a música A da playlist 1 para a playlist 2". */
@Component
class MoveTrack extends PlaylistSubcommand {

    MoveTrack(PlaylistService playlists, PlaylistOptions options) {
        super(playlists, options);
    }

    @Override
    public SubcommandData definition() {
        return new SubcommandData("move", "Move uma música de uma playlist para outra")
                .addOptions(
                        PlaylistOptions.playlist(PlaylistOptions.FROM, "Playlist de origem"),
                        PlaylistOptions.track(),
                        PlaylistOptions.playlist(PlaylistOptions.TO, "Playlist de destino"));
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
}
