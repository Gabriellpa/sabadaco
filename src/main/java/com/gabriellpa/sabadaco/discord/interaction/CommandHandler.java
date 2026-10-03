package com.gabriellpa.sabadaco.discord.interaction;

import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

/**
 * Comportamento comum de {@link SlashCommand} e {@link Subcommand}.
 * Roda em uma virtual thread: pode bloquear (carregar música, buscar), desde que responda ou faça
 * {@code deferReply()} em até 3 segundos.
 */
public interface CommandHandler {

    void handle(SlashCommandInteractionEvent event);

    /** Ajuda exibida no {@code /help}. Obrigatória: comando sem ajuda não compila. */
    CommandHelp help();

    /** Sugestões para opções com autocomplete. */
    default void autocomplete(CommandAutoCompleteInteractionEvent event) {
    }
}
