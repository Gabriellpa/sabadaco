package com.gabriellpa.sabadaco.discord.command;

import com.gabriellpa.sabadaco.discord.interaction.Interactions;
import com.gabriellpa.sabadaco.discord.interaction.SlashCommand;
import com.gabriellpa.sabadaco.discord.ui.PlayerPanelUpdater;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.springframework.stereotype.Component;

/** {@code /player}: traz o painel de controle para o canal atual. */
@Component
@RequiredArgsConstructor
public class PlayerCommand implements SlashCommand {

    private final PlayerPanelUpdater panels;

    @Override
    public SlashCommandData definition() {
        return Commands.slash("player", "Mostra o painel do player neste canal");
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        panels.publish(Interactions.guildId(event), event.getMessageChannel());
        event.reply("🎛️ Painel enviado.").setEphemeral(true).queue();
    }
}
