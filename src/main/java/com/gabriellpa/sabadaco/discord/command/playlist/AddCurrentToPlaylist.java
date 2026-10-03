package com.gabriellpa.sabadaco.discord.command.playlist;

import com.gabriellpa.sabadaco.UserFacingException;
import com.gabriellpa.sabadaco.discord.interaction.Interactions;
import com.gabriellpa.sabadaco.discord.ui.Format;
import com.gabriellpa.sabadaco.music.MusicService;
import com.gabriellpa.sabadaco.music.PlayerSnapshot;
import com.gabriellpa.sabadaco.playlist.PlaylistService;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.springframework.stereotype.Component;

@Component
class AddCurrentToPlaylist extends PlaylistSubcommand {

    private final MusicService musicService;

    AddCurrentToPlaylist(PlaylistService playlists, PlaylistOptions options, MusicService musicService) {
        super(playlists, options);
        this.musicService = musicService;
    }

    @Override
    public SubcommandData definition() {
        return new SubcommandData("add-current", "Saves the song that is playing to the playlist")
                .addOptions(PlaylistOptions.playlist(PlaylistOptions.PLAYLIST, "Playlist"), PlaylistOptions.alias(false));
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        var playlist = options.resolve(event, PlaylistOptions.PLAYLIST);
        var current = musicService.snapshot(Interactions.guildId(event))
                .filter(PlayerSnapshot::playing)
                .orElseThrow(() -> new UserFacingException("Nada tocando agora."))
                .current().track();
        var added = playlists.addTrack(event.getUser().getIdLong(), playlist.id(), current,
                Interactions.string(event, PlaylistOptions.ALIAS));
        event.reply("💾 **%s** salva na playlist **%s**.".formatted(
                Format.escape(added.displayName()), Format.escape(playlist.name()))).setEphemeral(true).queue();
    }
}
