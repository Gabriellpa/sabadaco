package com.gabriellpa.sabadaco.discord.command.playlist;

import com.gabriellpa.sabadaco.discord.interaction.Interactions;
import com.gabriellpa.sabadaco.discord.ui.Format;
import com.gabriellpa.sabadaco.playlist.PlaylistService;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.springframework.stereotype.Component;

@Component
class RenamePlaylist extends PlaylistSubcommand {

    RenamePlaylist(PlaylistService playlists, PlaylistOptions options) {
        super(playlists, options);
    }

    @Override
    public SubcommandData definition() {
        return new SubcommandData("rename", "Renomeia uma playlist")
                .addOptions(
                        PlaylistOptions.playlist(PlaylistOptions.PLAYLIST, "Playlist"),
                        new OptionData(OptionType.STRING, "name", "Novo nome", true).setMaxLength(50));
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        var playlist = options.resolve(event, PlaylistOptions.PLAYLIST);
        var renamed = playlists.rename(event.getUser().getIdLong(), playlist.id(), Interactions.requireString(event, "name"));
        event.reply("✏️ **%s** agora se chama **%s**.".formatted(
                Format.escape(playlist.name()), Format.escape(renamed.name()))).setEphemeral(true).queue();
    }
}
