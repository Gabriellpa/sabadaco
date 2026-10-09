package com.gabriellpa.sabadaco.discord.command.playlist;

import com.gabriellpa.sabadaco.discord.DiscordDirectory;
import com.gabriellpa.sabadaco.discord.interaction.CommandHelp.Category;
import com.gabriellpa.sabadaco.discord.interaction.CommandHelp;
import com.gabriellpa.sabadaco.discord.interaction.Interactions;
import com.gabriellpa.sabadaco.discord.ui.PlaylistView;
import com.gabriellpa.sabadaco.playlist.PlaylistService;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.gabriellpa.sabadaco.discord.interaction.CommandHelp.example;

@Component
class ListPlaylists extends PlaylistSubcommand {

    private final DiscordDirectory directory;

    ListPlaylists(PlaylistService playlists, PlaylistOptions options, DiscordDirectory directory) {
        super(playlists, options);
        this.directory = directory;
    }

    @Override
    public SubcommandData definition() {
        return new SubcommandData("list", "Lists your playlists (this server and global)");
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        long userId = event.getUser().getIdLong();
        var visible = playlists.visibleTo(userId, Interactions.guildId(event));
        var text = PlaylistView.list(visible);
        if (playlists.isAdmin(userId)) {
            var others = playlists.browsableBy(userId, Interactions.guildId(event)).stream()
                    .filter(playlist -> playlist.ownerId() != userId)
                    .toList();
            text += PlaylistView.othersForAdmin(others, directory::userName);
        }
        event.reply(text).setEphemeral(true).queue();
    }

    @Override
    public CommandHelp help() {
        return CommandHelp.of(Category.PLAYLIST, "Lista suas playlists deste servidor e as globais.",
                List.of(example("/playlist list", "Mostra nome, escopo (🌐 global) e quantidade de músicas.")));
    }
}
