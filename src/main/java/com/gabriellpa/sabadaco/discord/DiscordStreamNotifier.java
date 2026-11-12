package com.gabriellpa.sabadaco.discord;

import com.gabriellpa.sabadaco.stream.ChatRelay;
import com.gabriellpa.sabadaco.stream.StreamNotifier;
import com.gabriellpa.sabadaco.stream.StreamSession;
import org.springframework.stereotype.Component;

/** Stream aberta pela Atividade: card "ao vivo" no chat do canal de voz e 🎥 no apelido de quem transmite. */
@Component
public class DiscordStreamNotifier implements StreamNotifier {

    private final StreamAnnouncements announcements;
    private final StreamerBadge badge;
    private final ChatRelay chat;

    public DiscordStreamNotifier(StreamAnnouncements announcements, StreamerBadge badge, ChatRelay chat) {
        this.announcements = announcements;
        this.badge = badge;
        this.chat = chat;
    }

    @Override
    public void started(StreamSession stream) {
        announcements.post(stream, chat.avatarUrl(stream.guildId(), stream.hostUserId()));
        badge.mark(stream);
    }
}
