package com.gabriellpa.sabadaco.discord.command;

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

@Component
@RequiredArgsConstructor
public class QueueCommand implements SlashCommand {

    private final MusicService musicService;

    @Override
    public SlashCommandData definition() {
        return Commands.slash("queue", "Mostra a fila do servidor");
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        var snapshot = musicService.snapshot(Interactions.guildId(event)).orElse(null);
        event.reply(Messages.create(QueueView.render(snapshot, 0))).setEphemeral(true).queue();
    }
}
