package com.gabriellpa.sabadaco.discord.command;

import com.gabriellpa.sabadaco.discord.interaction.CommandHelp.Category;
import com.gabriellpa.sabadaco.discord.interaction.CommandHelp;
import com.gabriellpa.sabadaco.discord.interaction.Interactions;
import com.gabriellpa.sabadaco.discord.interaction.SlashCommand;
import com.gabriellpa.sabadaco.discord.ui.Format;
import com.gabriellpa.sabadaco.discord.ui.Messages;
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
public class SkipCommand implements SlashCommand {

    private final MusicService musicService;

    @Override
    public SlashCommandData definition() {
        return Commands.slash("skip", "Skips the current song");
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        musicService.skip(Interactions.guildId(event));
        event.reply(Messages.text("⏭️ " + Format.mention(event.getUser().getIdLong()) + " pulou a música.")).queue();
    }

    @Override
    public CommandHelp help() {
        return CommandHelp.of(Category.CONTROLS, "Pula para a próxima música.",
                List.of(example("/skip", "Pula a atual. Funciona mesmo com o loop de música ligado.")));
    }
}
