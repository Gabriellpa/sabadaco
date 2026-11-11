package com.gabriellpa.sabadaco.discord.component;

import com.gabriellpa.sabadaco.UserFacingException;
import com.gabriellpa.sabadaco.discord.StreamInvites;
import com.gabriellpa.sabadaco.discord.interaction.ComponentHandler;
import com.gabriellpa.sabadaco.discord.interaction.Interactions;
import com.gabriellpa.sabadaco.discord.ui.Format;
import com.gabriellpa.sabadaco.discord.ui.StreamCard;
import com.gabriellpa.sabadaco.stream.StreamService;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.springframework.stereotype.Component;

/** Botão "Assistir" do card da stream: manda para quem clicou o link e o código (igual ao {@code /stream join}). */
@Component
@RequiredArgsConstructor
public class StreamButtons implements ComponentHandler {

    private final StreamService streams;
    private final StreamInvites invites;

    @Override
    public String prefix() {
        return StreamCard.PREFIX;
    }

    @Override
    public void onButton(ButtonInteractionEvent event, String action, String payload) {
        if (!StreamCard.JOIN.equals(action)) {
            throw new UserFacingException("Ação desconhecida: " + action);
        }
        event.deferReply(true).queue();
        var invite = streams.joinStream(Interactions.guildId(event), event.getUser().getIdLong(),
                Interactions.displayName(event), Long.parseLong(payload));
        invites.deliver(event, invite, "📺 **%s** de %s.".formatted(Format.escape(invite.stream().title()),
                Format.escape(invite.stream().hostName())));
    }
}
