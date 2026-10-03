package com.gabriellpa.sabadaco.discord.command;

import com.gabriellpa.sabadaco.discord.interaction.CommandHelp.Category;
import com.gabriellpa.sabadaco.discord.interaction.CommandHelp;
import com.gabriellpa.sabadaco.discord.interaction.SlashCommand;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.gabriellpa.sabadaco.discord.interaction.CommandHelp.example;

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

    @Override
    public CommandHelp help() {
        return CommandHelp.of(Category.OTHER, "Toca o clássico Kasino no Sabadaço (07/10/2006), com Gilberto Barros.",
                List.of(example("/kassino", "Coloca o Kasino para tocar na hora (ou na fila, se já houver música).")),
                "Você precisa estar em um canal de voz.");
    }
}
