package com.gabriellpa.sabadaco.discord.command.playlist;

import com.gabriellpa.sabadaco.discord.command.PlaybackReplies;
import com.gabriellpa.sabadaco.discord.interaction.CommandHelp.Category;
import com.gabriellpa.sabadaco.discord.interaction.CommandHelp;
import com.gabriellpa.sabadaco.discord.interaction.Interactions;
import com.gabriellpa.sabadaco.playlist.PlaylistService;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.gabriellpa.sabadaco.discord.interaction.CommandHelp.example;

@Component
class PlayPlaylist extends PlaylistSubcommand {

    private final PlaybackReplies playback;

    PlayPlaylist(PlaylistService playlists, PlaylistOptions options, PlaybackReplies playback) {
        super(playlists, options);
        this.playback = playback;
    }

    @Override
    public SubcommandData definition() {
        return new SubcommandData("play", "Plays a playlist (single songs already queued play first)")
                .addOptions(PlaylistOptions.playlist(PlaylistOptions.PLAYLIST, "Playlist"))
                .addOption(OptionType.BOOLEAN, "shuffle", "Random order", false);
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        playback.playPlaylist(event, options.resolve(event, PlaylistOptions.PLAYLIST), Interactions.bool(event, "shuffle"));
    }

    @Override
    public CommandHelp help() {
        return CommandHelp.of(Category.PLAYLIST, "Toca uma playlist inteira.",
                List.of(
                        example("/playlist play Clássicos do Sabadaço", "Coloca todas as músicas na fila, na ordem."),
                        example("/playlist play Clássicos do Sabadaço shuffle: True", "Mesma coisa, em ordem aleatória.")),
                "Músicas avulsas que já estão na fila tocam antes da playlist: o bot avisa.");
    }
}
