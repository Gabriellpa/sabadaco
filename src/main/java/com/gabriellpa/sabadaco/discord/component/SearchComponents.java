package com.gabriellpa.sabadaco.discord.component;

import com.gabriellpa.sabadaco.UserFacingException;
import com.gabriellpa.sabadaco.discord.command.PlaybackReplies;
import com.gabriellpa.sabadaco.discord.interaction.ComponentHandler;
import com.gabriellpa.sabadaco.discord.interaction.CustomId;
import com.gabriellpa.sabadaco.discord.interaction.Interactions;
import com.gabriellpa.sabadaco.discord.ui.Format;
import com.gabriellpa.sabadaco.discord.ui.SearchView;
import com.gabriellpa.sabadaco.music.MusicService;
import com.gabriellpa.sabadaco.playlist.PlaylistService;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import net.dv8tion.jda.api.modals.Modal;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Ações dos resultados de busca: tocar (botão) e salvar em playlist (select → modal com playlist e apelido).
 */
@Component
@RequiredArgsConstructor
public class SearchComponents implements ComponentHandler {

    private static final String PLAYLIST_FIELD = "playlist";
    private static final String ALIAS_FIELD = "alias";
    private static final int MAX_OPTIONS = 25;

    private final PlaybackReplies playback;
    private final MusicService musicService;
    private final PlaylistService playlistService;

    @Override
    public String prefix() {
        return SearchView.PREFIX;
    }

    @Override
    public void onButton(ButtonInteractionEvent event, String action, String payload) {
        if (!action.equals("play")) {
            throw new UserFacingException("Ação desconhecida: " + action);
        }
        playback.play(event, payload);
    }

    @Override
    public void onSelect(StringSelectInteractionEvent event, String action, String payload) {
        var uri = event.getValues().getFirst();
        var playlists = playlistService.visibleTo(event.getUser().getIdLong(), Interactions.guildId(event));
        if (playlists.isEmpty()) {
            throw new UserFacingException("Você ainda não tem playlists. Crie uma com `/playlist create`.");
        }
        var menu = StringSelectMenu.create(PLAYLIST_FIELD).setPlaceholder("Escolha a playlist");
        playlists.stream().limit(MAX_OPTIONS)
                .forEach(playlist -> menu.addOption(Format.truncate(playlist.label(), 100), playlist.id()));

        event.replyModal(Modal.create(CustomId.of(SearchView.PREFIX, "save", uri), "Salvar em playlist")
                .addComponents(
                        Label.of("Playlist", menu.build()),
                        Label.of("Apelido (opcional)", "Um nome curto para achar a música fácil",
                                TextInput.create(ALIAS_FIELD, TextInputStyle.SHORT)
                                        .setRequired(false)
                                        .setMaxLength(40)
                                        .setPlaceholder("ex.: abertura do kassino")
                                        .build()))
                .build()).queue();
    }

    @Override
    public void onModal(ModalInteractionEvent event, String action, String payload) {
        var playlistId = event.getValue(PLAYLIST_FIELD).getAsStringList().getFirst();
        var alias = Optional.ofNullable(event.getValue(ALIAS_FIELD)).map(ModalMapping::getAsString).orElse(null);
        event.deferReply(true).queue();
        var playlist = playlistService.owned(event.getUser().getIdLong(), playlistId);
        var track = musicService.resolve(payload);
        playlistService.addTrack(event.getUser().getIdLong(), playlistId, track, alias);
        event.getHook().sendMessage("💾 **%s** salva na playlist **%s**.".formatted(
                Format.escape(track.title()), Format.escape(playlist.name()))).queue();
    }
}
