package com.gabriellpa.sabadaco.discord.command.playlist;

import com.gabriellpa.sabadaco.discord.interaction.Subcommand;
import com.gabriellpa.sabadaco.playlist.PlaylistService;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;

/**
 * Base dos subcomandos de {@code /playlist}: define o pai e reaproveita o autocomplete.
 */
abstract class PlaylistSubcommand implements Subcommand {

    protected final PlaylistService playlists;
    protected final PlaylistOptions options;

    protected PlaylistSubcommand(PlaylistService playlists, PlaylistOptions options) {
        this.playlists = playlists;
        this.options = options;
    }

    @Override
    public String parent() {
        return PlaylistCommandGroup.NAME;
    }

    @Override
    public void autocomplete(CommandAutoCompleteInteractionEvent event) {
        options.autocomplete(event);
    }
}
