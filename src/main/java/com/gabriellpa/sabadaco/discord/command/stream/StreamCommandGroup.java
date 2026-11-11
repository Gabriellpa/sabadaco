package com.gabriellpa.sabadaco.discord.command.stream;

import com.gabriellpa.sabadaco.discord.interaction.CommandGroup;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.springframework.stereotype.Component;

@Component
public class StreamCommandGroup implements CommandGroup {

    public static final String NAME = "stream";

    @Override
    public SlashCommandData definition() {
        return Commands.slash(NAME, "Screen streaming in your voice channel");
    }
}
