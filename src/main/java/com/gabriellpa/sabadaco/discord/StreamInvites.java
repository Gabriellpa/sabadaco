package com.gabriellpa.sabadaco.discord;

import com.gabriellpa.sabadaco.discord.ui.Format;
import com.gabriellpa.sabadaco.stream.StreamService.Invite;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import org.springframework.stereotype.Component;

/**
 * Entrega link e código por DM. Se a pessoa bloqueia DMs do servidor, o código vai numa resposta efêmera
 * (só ela vê), que é tão privada quanto a DM.
 */
@Slf4j
@Component
public class StreamInvites {

    /** Envia a DM e devolve {@code true} se deu certo. Bloqueia: os comandos rodam em virtual threads. */
    boolean sendByDm(IReplyCallback event, Invite invite) {
        try {
            event.getUser().openPrivateChannel()
                    .flatMap(channel -> channel.sendMessage(message(invite)))
                    .complete();
            return true;
        } catch (ErrorResponseException error) {
            log.debug("DM bloqueada para {}: {}", event.getUser().getIdLong(), error.getMeaning());
            return false;
        }
    }

    /** Responde (efêmero) dizendo que o código foi por DM ou, se a DM falhou, com o próprio código. */
    public void deliver(IReplyCallback event, Invite invite, String headline) {
        if (sendByDm(event, invite)) {
            event.getHook().sendMessage(headline + "\n📬 Link e código enviados por DM.").setEphemeral(true).queue();
        } else {
            event.getHook().sendMessage(headline + "\n⚠️ Não consegui mandar DM (estão bloqueadas?). Aqui, só você vê:\n\n"
                    + message(invite)).setEphemeral(true).queue();
        }
    }

    static String message(Invite invite) {
        var stream = invite.stream();
        return """
                📺 **%s** — stream de %s
                🔗 %s
                🔑 Código: `%s`
                Vale por 5 minutos, uma vez só e só nesse link. Você precisa estar no canal de voz da stream."""
                .formatted(Format.escape(stream.title()), Format.escape(stream.hostName()), invite.link(),
                        invite.code().code());
    }
}
