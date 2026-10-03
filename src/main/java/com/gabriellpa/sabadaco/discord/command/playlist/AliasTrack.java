package com.gabriellpa.sabadaco.discord.command.playlist;

import com.gabriellpa.sabadaco.discord.interaction.Interactions;
import com.gabriellpa.sabadaco.discord.ui.Format;
import com.gabriellpa.sabadaco.playlist.PlaylistService;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.springframework.stereotype.Component;

@Component
class AliasTrack extends PlaylistSubcommand {

    AliasTrack(PlaylistService playlists, PlaylistOptions options) {
        super(playlists, options);
    }

    @Override
    public SubcommandData definition() {
        return new SubcommandData("alias", "Dá (ou remove) um apelido para uma música da playlist")
                .addOptions(
                        PlaylistOptions.playlist(PlaylistOptions.PLAYLIST, "Playlist"),
                        PlaylistOptions.track(),
                        PlaylistOptions.alias(false).setDescription("Novo apelido (vazio remove)"));
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
}
