package com.gabriellpa.sabadaco.discord.command.playlist;

import com.gabriellpa.sabadaco.discord.interaction.Interactions;
import com.gabriellpa.sabadaco.discord.ui.Format;
import com.gabriellpa.sabadaco.music.MusicService;
import com.gabriellpa.sabadaco.playlist.PlaylistService;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.springframework.stereotype.Component;

@Component
class AddToPlaylist extends PlaylistSubcommand {

    private final MusicService musicService;

    AddToPlaylist(PlaylistService playlists, PlaylistOptions options, MusicService musicService) {
        super(playlists, options);
        this.musicService = musicService;
    }

    @Override
    public SubcommandData definition() {
        return new SubcommandData("add", "Adds a song (URL or search) to the playlist")
                .addOptions(PlaylistOptions.playlist(PlaylistOptions.PLAYLIST, "Playlist"))
                .addOption(OptionType.STRING, "query", "Song URL or name", true)
                .addOptions(PlaylistOptions.alias(false));
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        var playlist = options.resolve(event, PlaylistOptions.PLAYLIST);
        event.deferReply(true).queue();
        var track = musicService.resolve(Interactions.requireString(event, "query"));
        var added = playlists.addTrack(event.getUser().getIdLong(), playlist.id(), track,
                Interactions.string(event, PlaylistOptions.ALIAS));
        event.getHook().sendMessage("➕ **%s** adicionada à playlist **%s**.".formatted(
                Format.escape(added.displayName()), Format.escape(playlist.name()))).queue();
    }
}
