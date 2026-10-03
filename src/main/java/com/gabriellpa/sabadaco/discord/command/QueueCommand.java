package com.gabriellpa.sabadaco.discord.command;

import com.gabriellpa.sabadaco.discord.interaction.CommandHelp.Category;
import com.gabriellpa.sabadaco.discord.interaction.CommandHelp;
import com.gabriellpa.sabadaco.discord.interaction.Interactions;
import com.gabriellpa.sabadaco.discord.interaction.SlashCommand;
import com.gabriellpa.sabadaco.discord.ui.Messages;
import com.gabriellpa.sabadaco.discord.ui.QueueView;
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
public class QueueCommand implements SlashCommand {

    private final MusicService musicService;

    @Override
    public SlashCommandData definition() {
        return Commands.slash("queue", "Shows the server queue");
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        var snapshot = musicService.snapshot(Interactions.guildId(event)).orElse(null);
        event.reply(Messages.create(QueueView.render(snapshot, 0))).setEphemeral(true).queue();
    }

    @Override
    public CommandHelp help() {
        return CommandHelp.of(Category.CONTROLS, "Mostra a fila do servidor.",
                List.of(example("/queue", "Fila paginada: ◀️ ▶️ para navegar, menu 🗑️ para remover uma música e \"Limpar fila\".")),
                "A fila mostra separado as músicas avulsas (tocam primeiro) e as da playlist.");
    }
}
