package com.gabriellpa.sabadaco.discord.command;

import com.gabriellpa.sabadaco.discord.interaction.CommandHelp.Category;
import com.gabriellpa.sabadaco.discord.interaction.CommandHelp;
import com.gabriellpa.sabadaco.discord.interaction.Interactions;
import com.gabriellpa.sabadaco.discord.interaction.SlashCommand;
import com.gabriellpa.sabadaco.music.MusicService;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.gabriellpa.sabadaco.discord.interaction.CommandHelp.example;

@Component
@RequiredArgsConstructor
public class PauseCommand implements SlashCommand {

    private final MusicService musicService;

    @Override
    public SlashCommandData definition() {
        return Commands.slash("pause", "Pauses or resumes the music");
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        boolean paused = musicService.togglePause(Interactions.guildId(event));
        event.reply(paused ? "⏸️ Pausado." : "▶️ Continuando.").queue();
    }

    @Override
    public CommandHelp help() {
        return CommandHelp.of(Category.CONTROLS, "Pausa ou continua a música.",
                List.of(example("/pause", "Pausa; usar de novo continua de onde parou.")));
    }
}
