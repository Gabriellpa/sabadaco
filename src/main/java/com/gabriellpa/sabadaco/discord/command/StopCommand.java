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
public class StopCommand implements SlashCommand {

    private final MusicService musicService;

    @Override
    public SlashCommandData definition() {
        return Commands.slash("stop", "Stops the music, clears the queue and leaves the channel");
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        musicService.stop(Interactions.guildId(event));
        event.reply(Messages.text("⏹️ " + Format.mention(event.getUser().getIdLong()) + " parou o player.")).queue();
    }

    @Override
    public CommandHelp help() {
        return CommandHelp.of(Category.CONTROLS, "Para tudo: limpa a fila e o bot sai do canal.",
                List.of(example("/stop", "Encerra a música, esvazia a fila e desconecta do canal de voz.")));
    }
}
