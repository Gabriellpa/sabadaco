package com.gabriellpa.sabadaco.discord.command;

import com.gabriellpa.sabadaco.discord.interaction.CommandHelp.Category;
import com.gabriellpa.sabadaco.discord.interaction.CommandHelp;
import com.gabriellpa.sabadaco.discord.interaction.Interactions;
import com.gabriellpa.sabadaco.discord.interaction.SlashCommand;
import com.gabriellpa.sabadaco.discord.ui.PlayerPanelUpdater;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.gabriellpa.sabadaco.discord.interaction.CommandHelp.example;

/** {@code /player}: traz o painel de controle para o canal atual. */
@Component
@RequiredArgsConstructor
public class PlayerCommand implements SlashCommand {

    private final PlayerPanelUpdater panels;

    @Override
    public SlashCommandData definition() {
        return Commands.slash("player", "Shows the player panel in this channel");
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        panels.publish(Interactions.guildId(event), event.getMessageChannel());
        event.reply("🎛️ Painel enviado.").setEphemeral(true).queue();
    }

    @Override
    public CommandHelp help() {
        return CommandHelp.of(Category.CONTROLS, "Traz o painel do player para o canal atual.",
                List.of(example("/player", "Envia o painel com capa, progresso e os botões ⏯️ ⏭️ ⏹️ 🔀 🔁 🔉 🔊 📜.")),
                "O painel se atualiza sozinho, inclusive quando alguém usa o painel admin.",
                "Só existe um painel por servidor: chamar de novo apaga o anterior.");
    }
}
