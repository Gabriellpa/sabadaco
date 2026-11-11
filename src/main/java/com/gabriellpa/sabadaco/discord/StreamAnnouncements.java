package com.gabriellpa.sabadaco.discord;

import com.gabriellpa.sabadaco.discord.ui.Format;
import com.gabriellpa.sabadaco.discord.ui.Messages;
import com.gabriellpa.sabadaco.discord.ui.StreamCard;
import com.gabriellpa.sabadaco.stream.StreamSession;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * O card de cada stream no canal de texto: publica ao abrir e, quando a stream termina, troca pelo card de
 * encerrada (sem o botão). Os cards ficam em memória: depois de reiniciar o bot, o fim vira uma mensagem simples.
 */
@Slf4j
@Component
public class StreamAnnouncements {

    private record CardRef(long channelId, long messageId, String avatarUrl) {
    }

    private final JDA jda;
    private final Clock clock;
    private final Map<Long, CardRef> cards = new ConcurrentHashMap<>();

    @Autowired
    public StreamAnnouncements(@Lazy JDA jda) {
        this(jda, Clock.systemUTC());
    }

    StreamAnnouncements(JDA jda, Clock clock) {
        this.jda = jda;
        this.clock = clock;
    }

    /** Responde o comando com o card ao vivo e guarda a mensagem para editar no fim. */
    public void announce(IReplyCallback event, StreamSession stream, String avatarUrl) {
        event.reply(Messages.create(StreamCard.live(stream, avatarUrl))).queue(hook -> hook.retrieveOriginal().queue(
                message -> cards.put(stream.id(), new CardRef(message.getChannelIdLong(), message.getIdLong(), avatarUrl)),
                error -> log.debug("Não consegui guardar o card da stream {}", stream.id(), error)));
    }

    /** Troca o card pelo de encerrada. Sem card guardado (bot reiniciou), avisa com uma mensagem simples. */
    public void ended(StreamSession stream, String reason) {
        var card = cards.remove(stream.id());
        var channel = jda.getChannelById(GuildMessageChannel.class, card == null ? stream.textChannelId() : card.channelId());
        if (channel == null || !channel.canTalk()) {
            return;
        }
        if (card != null) {
            channel.editMessageById(card.messageId(), Messages.edit(StreamCard.ended(stream, card.avatarUrl(), reason, clock.instant())))
                    .queue(null, error -> log.debug("Card da stream {} sumiu", stream.id(), error));
        } else {
            channel.sendMessage(Messages.text("⏹️ A stream **%s** de %s terminou: %s."
                    .formatted(Format.escape(stream.title()), Format.escape(stream.hostName()), reason))).queue();
        }
    }
}
