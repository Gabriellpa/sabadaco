package com.gabriellpa.sabadaco.discord.command.playlist;

import com.gabriellpa.sabadaco.discord.command.PlaybackReplies;
import com.gabriellpa.sabadaco.discord.interaction.Interactions;
import com.gabriellpa.sabadaco.playlist.PlaylistService;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.springframework.stereotype.Component;

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
}
