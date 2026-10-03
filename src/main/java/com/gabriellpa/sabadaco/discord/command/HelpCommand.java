package com.gabriellpa.sabadaco.discord.command;

import com.gabriellpa.sabadaco.UserFacingException;
import com.gabriellpa.sabadaco.discord.interaction.CommandHelp;
import com.gabriellpa.sabadaco.discord.interaction.CommandHelp.Category;
import com.gabriellpa.sabadaco.discord.interaction.HelpCatalog;
import com.gabriellpa.sabadaco.discord.interaction.Interactions;
import com.gabriellpa.sabadaco.discord.interaction.SlashCommand;
import com.gabriellpa.sabadaco.discord.ui.HelpView;
import com.gabriellpa.sabadaco.discord.ui.Messages;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

import static com.gabriellpa.sabadaco.discord.interaction.CommandHelp.example;

/** {@code /help [comando]}: lista os comandos por categoria ou mostra exemplos de um comando. */
@Component
@RequiredArgsConstructor
public class HelpCommand implements SlashCommand {

    private static final String COMMAND = "command";
    private static final int MAX_CHOICES = 25;

    private final HelpCatalog catalog;

    @Override
    public SlashCommandData definition() {
        return Commands.slash("help", "Shows every command with examples")
                .addOptions(new OptionData(OptionType.STRING, COMMAND, "Go straight to one command", false, true));
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        var wanted = Interactions.string(event, COMMAND);
        var components = wanted == null
                ? HelpView.overview(catalog.entries())
                : HelpView.detail(catalog.find(wanted)
                        .orElseThrow(() -> new UserFacingException("Comando " + wanted + " não encontrado. Use /help para ver a lista.")));
        event.reply(Messages.create(components)).setEphemeral(true).queue();
    }

    @Override
    public void autocomplete(CommandAutoCompleteInteractionEvent event) {
        var typed = event.getFocusedOption().getValue().toLowerCase(Locale.ROOT).replaceFirst("^/", "");
        event.replyChoices(catalog.entries().stream()
                .filter(entry -> entry.key().contains(typed) || entry.ptName().contains(typed))
                .limit(MAX_CHOICES)
                .map(entry -> new Command.Choice(entry.display() + " · " + entry.ptName(), entry.key()))
                .toList()).queue();
    }

    @Override
    public CommandHelp help() {
        return CommandHelp.of(Category.OTHER, "Mostra todos os comandos, com exemplos.",
                List.of(
                        example("/help", "Lista os comandos por categoria; escolha um no menu para ver os exemplos."),
                        example("/help play", "Vai direto para os exemplos do /play.")),
                "A resposta só aparece para você.");
    }
}
