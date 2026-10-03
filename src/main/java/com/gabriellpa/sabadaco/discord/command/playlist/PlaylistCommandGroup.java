package com.gabriellpa.sabadaco.discord.command.playlist;

import com.gabriellpa.sabadaco.discord.interaction.CommandGroup;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.springframework.stereotype.Component;

@Component
public class PlaylistCommandGroup implements CommandGroup {

    public static final String NAME = "playlist";

    @Override
    public SlashCommandData definition() {
        return Commands.slash(NAME, "Your playlists (this server or global)");
    }
}
