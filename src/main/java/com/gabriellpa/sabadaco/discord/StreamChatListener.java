package com.gabriellpa.sabadaco.discord;

import com.gabriellpa.sabadaco.stream.StreamChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

/**
 * Espelha na página as mensagens do canal de texto onde a stream foi criada.
 *
 * <p>Só recebe eventos com {@code DISCORD_MESSAGE_CONTENT=true} (intent privilegiada, precisa estar ligada no
 * Developer Portal). Sem ela o chat da página continua mandando para o Discord, mas não mostra o que é escrito lá.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StreamChatListener extends ListenerAdapter {

    private final StreamChatService chat;
    private final DiscordChatRelay relay;

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (!event.isFromGuild() || !chat.mirrors(event.getGuild().getIdLong(), event.getChannel().getIdLong())) {
            return;
        }
        var message = event.getMessage();
        var author = event.getAuthor();
        if (author.getIdLong() == event.getJDA().getSelfUser().getIdLong()
                || message.isWebhookMessage() && relay.isOwnWebhook(author.getIdLong())) {
            return; // eco do que veio da página: ela já mostrou
        }
        var name = event.getMember() != null ? event.getMember().getEffectiveName() : author.getEffectiveName();
        var avatar = event.getMember() != null ? event.getMember().getEffectiveAvatarUrl() : author.getEffectiveAvatarUrl();
        var content = content(message);
        var sentAt = message.getTimeCreated().toInstant();
        // Envia dados ao LiveKit por HTTP: sai da thread de eventos do JDA
        Thread.ofVirtual().start(() -> {
            try {
                chat.fromDiscord(event.getGuild().getIdLong(), event.getChannel().getIdLong(), message.getId(), name,
                        avatar, content, sentAt);
            } catch (RuntimeException error) {
                log.warn("Falha ao espelhar mensagem {} na stream", message.getId(), error);
            }
        });
    }

    /** Texto com menções já como nomes; anexos viram "📎 arquivo". */
    static String content(Message message) {
        var attachments = message.getAttachments().stream()
                .map(attachment -> "📎 " + attachment.getFileName())
                .collect(Collectors.joining(" "));
        var text = message.getContentDisplay();
        if (attachments.isEmpty()) {
            return text;
        }
        return text.isBlank() ? attachments : text + " " + attachments;
    }
}
