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

@Component
class RemoveTrack extends PlaylistSubcommand {

    RemoveTrack(PlaylistService playlists, PlaylistOptions options) {
        super(playlists, options);
    }

    @Override
    public SubcommandData definition() {
        return new SubcommandData("remove", "Removes a song from the playlist")
                .addOptions(PlaylistOptions.playlist(PlaylistOptions.PLAYLIST, "Playlist"), PlaylistOptions.track());
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        var playlist = options.resolve(event, PlaylistOptions.PLAYLIST);
        var removed = playlists.removeTrack(event.getUser().getIdLong(), playlist.id(), PlaylistOptions.trackIndex(event));
        event.reply("🗑️ **%s** removida de **%s**.".formatted(
                Format.escape(removed.displayName()), Format.escape(playlist.name()))).setEphemeral(true).queue();
    }

    @Override
    public CommandHelp help() {
        return CommandHelp.of(Category.PLAYLIST, "Remove uma música da playlist.",
                List.of(example("/playlist remove Clássicos do Sabadaço 1", "Remove a música 1.")));
    }
}
