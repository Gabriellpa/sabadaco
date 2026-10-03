package com.gabriellpa.sabadaco.discord.command.playlist;

import com.gabriellpa.sabadaco.discord.interaction.CustomId;
import com.gabriellpa.sabadaco.discord.ui.Format;
import com.gabriellpa.sabadaco.discord.ui.PlaylistView;
import com.gabriellpa.sabadaco.playlist.PlaylistService;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.springframework.stereotype.Component;

/** Pede confirmação antes de apagar (o botão é tratado em PlaylistComponents). */
@Component
class DeletePlaylist extends PlaylistSubcommand {

    DeletePlaylist(PlaylistService playlists, PlaylistOptions options) {
        super(playlists, options);
    }

    @Override
    public SubcommandData definition() {
        return new SubcommandData("delete", "Apaga uma playlist")
                .addOptions(PlaylistOptions.playlist(PlaylistOptions.PLAYLIST, "Playlist"));
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        var playlist = options.resolve(event, PlaylistOptions.PLAYLIST);
        event.reply("Apagar a playlist **%s** com %d música(s)? Não dá para desfazer.".formatted(
                        Format.escape(playlist.name()), playlist.tracks().size()))
                .setComponents(ActionRow.of(
                        Button.danger(CustomId.of(PlaylistView.PREFIX, "delete", playlist.id()), "Apagar"),
                        Button.secondary(CustomId.of(PlaylistView.PREFIX, "cancel"), "Cancelar")))
                .setEphemeral(true).queue();
    }
}
