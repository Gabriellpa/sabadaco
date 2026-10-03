package com.gabriellpa.sabadaco.discord.command;

import com.gabriellpa.sabadaco.discord.interaction.Interactions;
import com.gabriellpa.sabadaco.discord.ui.EnqueueMessages;
import com.gabriellpa.sabadaco.discord.ui.Messages;
import com.gabriellpa.sabadaco.discord.ui.PlayerPanelUpdater;
import com.gabriellpa.sabadaco.music.EnqueueResult;
import com.gabriellpa.sabadaco.music.MusicService;
import com.gabriellpa.sabadaco.playlist.Playlist;
import com.gabriellpa.sabadaco.playlist.PlaylistTrack;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import org.springframework.stereotype.Component;

import java.util.function.LongFunction;

/**
 * Fluxo de "tocar" reaproveitado por comandos e botões: valida canal de voz, enfileira,
 * responde com o aviso de prioridade e garante que o servidor tenha um painel.
 */
@Component
@RequiredArgsConstructor
public class PlaybackReplies {

    private final MusicService musicService;
    private final PlayerPanelUpdater panels;

    public void play(IReplyCallback event, String query) {
        enqueue(event, voiceChannel -> musicService.play(
                Interactions.guildId(event), voiceChannel, event.getUser().getIdLong(), query));
    }

    public void playPlaylist(IReplyCallback event, Playlist playlist, boolean shuffle) {
        var uris = playlist.tracks().stream().map(PlaylistTrack::uri).toList();
        enqueue(event, voiceChannel -> musicService.playPlaylist(
                Interactions.guildId(event), voiceChannel, event.getUser().getIdLong(), playlist.name(), uris, shuffle));
    }

    private void enqueue(IReplyCallback event, LongFunction<EnqueueResult> action) {
        long guildId = Interactions.guildId(event);
        long voiceChannel = Interactions.requireVoiceChannel(event);
        event.deferReply().queue();
        var result = action.apply(voiceChannel);
        event.getHook().sendMessage(Messages.text(EnqueueMessages.describe(result, event.getUser().getIdLong()))).queue();
        if (!panels.hasPanel(guildId)) {
            panels.publish(guildId, event.getMessageChannel());
        }
    }
}
