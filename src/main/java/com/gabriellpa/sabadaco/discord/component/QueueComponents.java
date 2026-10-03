package com.gabriellpa.sabadaco.discord.component;

import com.gabriellpa.sabadaco.UserFacingException;
import com.gabriellpa.sabadaco.discord.interaction.ComponentHandler;
import com.gabriellpa.sabadaco.discord.interaction.Interactions;
import com.gabriellpa.sabadaco.discord.ui.Messages;
import com.gabriellpa.sabadaco.discord.ui.QueueView;
import com.gabriellpa.sabadaco.music.MusicService;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.callbacks.IMessageEditCallback;
import org.springframework.stereotype.Component;

/** Paginação, remoção e limpeza da fila. */
@Component
@RequiredArgsConstructor
public class QueueComponents implements ComponentHandler {

    private final MusicService musicService;

    @Override
    public String prefix() {
        return QueueView.PREFIX;
    }

    @Override
    public void onButton(ButtonInteractionEvent event, String action, String payload) {
        long guildId = Interactions.guildId(event);
        switch (action) {
            case "page" -> render(event, guildId, Integer.parseInt(payload));
            case "clear" -> {
                musicService.clearQueue(guildId);
                render(event, guildId, 0);
            }
            default -> throw new UserFacingException("Ação desconhecida: " + action);
        }
    }

    @Override
    public void onSelect(StringSelectInteractionEvent event, String action, String payload) {
        long guildId = Interactions.guildId(event);
        int index = Integer.parseInt(event.getValues().getFirst());
        musicService.removeFromQueue(guildId, index);
        render(event, guildId, Integer.parseInt(payload));
    }

    private void render(IMessageEditCallback event, long guildId, int page) {
        var snapshot = musicService.snapshot(guildId).orElse(null);
        event.editMessage(Messages.edit(QueueView.render(snapshot, page))).queue();
    }
}
