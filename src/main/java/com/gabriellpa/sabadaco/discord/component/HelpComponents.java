package com.gabriellpa.sabadaco.discord.component;

import com.gabriellpa.sabadaco.UserFacingException;
import com.gabriellpa.sabadaco.discord.interaction.ComponentHandler;
import com.gabriellpa.sabadaco.discord.interaction.HelpCatalog;
import com.gabriellpa.sabadaco.discord.ui.HelpView;
import com.gabriellpa.sabadaco.discord.ui.Messages;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import org.springframework.stereotype.Component;

/** Navegação do {@code /help}: escolher um comando no menu e voltar para a lista. */
@Component
@RequiredArgsConstructor
public class HelpComponents implements ComponentHandler {

    private final HelpCatalog catalog;

    @Override
    public String prefix() {
        return HelpView.PREFIX;
    }

    @Override
    public void onSelect(StringSelectInteractionEvent event, String action, String payload) {
        var entry = catalog.find(event.getValues().getFirst())
                .orElseThrow(() -> new UserFacingException("Comando não encontrado."));
        event.editMessage(Messages.edit(HelpView.detail(entry))).queue();
    }

    @Override
    public void onButton(ButtonInteractionEvent event, String action, String payload) {
        event.editMessage(Messages.edit(HelpView.overview(catalog.entries()))).queue();
    }
}
