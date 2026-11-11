package com.gabriellpa.sabadaco.discord;

import com.gabriellpa.sabadaco.stream.StreamService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.stereotype.Component;

/**
 * Quem sai (ou troca) de canal de voz perde o acesso às streams daquele canal: é desconectado da sala,
 * os códigos pendentes deixam de valer e, se transmitia lá, a stream termina com um aviso no canal de texto.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StreamVoiceListener extends ListenerAdapter {

    private final StreamService streams;
    private final StreamAnnouncements announcements;

    @Override
    public void onGuildVoiceUpdate(GuildVoiceUpdateEvent event) {
        var left = event.getChannelLeft();
        if (left == null || event.getMember().getUser().isBot()) {
            return; // entrou num canal (não saiu de nenhum) ou é bot
        }
        // A chamada ao LiveKit é HTTP: sai da thread de eventos do JDA
        Thread.ofVirtual().start(() -> {
            try {
                var ended = streams.leftVoiceChannel(event.getGuild().getIdLong(), event.getMember().getIdLong(), left.getIdLong());
                ended.forEach(stream -> announcements.ended(stream, "quem transmitia saiu do canal de voz"));
            } catch (RuntimeException error) {
                log.warn("Falha ao tratar saída do canal de voz de {}", event.getMember().getIdLong(), error);
            }
        });
    }
}
