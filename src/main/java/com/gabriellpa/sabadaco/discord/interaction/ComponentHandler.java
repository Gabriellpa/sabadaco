package com.gabriellpa.sabadaco.discord.interaction;

import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;

/**
 * Trata botões, selects e modais cujo customId começa com {@link #prefix()}.
 * Formato do customId: {@code prefixo:acao[:payload]} (veja {@link CustomId}); o payload pode conter ':'.
 */
public interface ComponentHandler {

    String prefix();

    default void onButton(ButtonInteractionEvent event, String action, String payload) {
        throw new UnsupportedOperationException("Botão não suportado: " + event.getComponentId());
    }

    default void onSelect(StringSelectInteractionEvent event, String action, String payload) {
        throw new UnsupportedOperationException("Select não suportado: " + event.getComponentId());
    }

    default void onModal(ModalInteractionEvent event, String action, String payload) {
        throw new UnsupportedOperationException("Modal não suportado: " + event.getModalId());
    }
}
