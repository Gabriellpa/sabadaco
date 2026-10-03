package com.gabriellpa.sabadaco.discord.command;

import com.gabriellpa.sabadaco.discord.interaction.SlashCommand;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KassinoCommand implements SlashCommand {

    private static final String KASSINO = "https://www.youtube.com/watch?v=LCDaw0QmQQc";

    private final PlaybackReplies playback;

    @Override
    public SlashCommandData definition() {
        return Commands.slash("kassino", "After years of sabadico our friend returns");
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        playback.play(event, KASSINO);
    }
}
