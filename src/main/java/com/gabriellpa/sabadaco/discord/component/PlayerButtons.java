package com.gabriellpa.sabadaco.discord.component;

import com.gabriellpa.sabadaco.UserFacingException;
import com.gabriellpa.sabadaco.discord.interaction.ComponentHandler;
import com.gabriellpa.sabadaco.discord.interaction.Interactions;
import com.gabriellpa.sabadaco.discord.ui.Messages;
import com.gabriellpa.sabadaco.discord.ui.PlayerPanel;
import com.gabriellpa.sabadaco.discord.ui.QueueView;
import com.gabriellpa.sabadaco.music.MusicService;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.springframework.stereotype.Component;

/** Botões do painel do player. */
@Component
@RequiredArgsConstructor
public class PlayerButtons implements ComponentHandler {

    private static final int VOLUME_STEP = 10;

    private final MusicService musicService;

    @Override
    public String prefix() {
        return PlayerPanel.PREFIX;
    }

    @Override
    public void onButton(ButtonInteractionEvent event, String action, String payload) {
        long guildId = Interactions.guildId(event);
        if (action.equals("queue")) {
            var snapshot = musicService.snapshot(guildId).orElse(null);
            event.reply(Messages.create(QueueView.render(snapshot, 0))).setEphemeral(true).queue();
            return;
        }
        switch (action) {
            case "pause" -> musicService.togglePause(guildId);
            case "skip" -> musicService.skip(guildId);
            case "stop" -> musicService.stop(guildId);
            case "shuffle" -> musicService.shuffle(guildId);
            case "loop" -> musicService.cycleLoop(guildId);
            case "voldown" -> changeVolume(guildId, -VOLUME_STEP);
            case "volup" -> changeVolume(guildId, VOLUME_STEP);
            default -> throw new UserFacingException("Ação desconhecida: " + action);
        }
        // Resposta imediata no painel clicado; o PlayerPanelUpdater também atualiza o painel oficial
        event.editMessage(Messages.edit(PlayerPanel.render(musicService.snapshot(guildId).orElse(null)))).queue();
    }

    private void changeVolume(long guildId, int delta) {
        var snapshot = musicService.snapshot(guildId).orElseThrow(() -> new UserFacingException("Nada tocando."));
        musicService.setVolume(guildId, snapshot.volume() + delta);
    }
}
