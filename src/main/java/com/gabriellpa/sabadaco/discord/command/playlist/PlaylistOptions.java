package com.gabriellpa.sabadaco.discord.command.playlist;

import com.gabriellpa.sabadaco.UserFacingException;
import com.gabriellpa.sabadaco.discord.DiscordDirectory;
import com.gabriellpa.sabadaco.discord.interaction.Interactions;
import com.gabriellpa.sabadaco.discord.ui.Format;
import com.gabriellpa.sabadaco.playlist.Playlist;
import com.gabriellpa.sabadaco.playlist.PlaylistScope;
import com.gabriellpa.sabadaco.playlist.PlaylistService;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.interactions.Interaction;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.CommandInteractionPayload;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.IntStream;

/**
 * Opções e autocomplete compartilhados pelos subcomandos de {@code /playlist}.
 * O autocomplete envia o id da playlist (resolve nomes iguais em escopos diferentes),
 * mas o usuário também pode digitar o nome. Admins também veem as playlists dos outros, com o nome do dono.
 */
@Component
@RequiredArgsConstructor
public class PlaylistOptions {

    public static final String PLAYLIST = "playlist";
    public static final String TRACK = "track";
    public static final String FROM = "from";
    public static final String TO = "to";
    public static final String ALIAS = "alias";
    private static final int MAX_CHOICES = 25;

    private final PlaylistService playlistService;
    private final DiscordDirectory directory;

    public static OptionData playlist(String name, String description) {
        return new OptionData(OptionType.STRING, name, description, true, true);
    }

    public static OptionData track() {
        return new OptionData(OptionType.INTEGER, TRACK, "Song number (or pick it by name)", true, true)
                .setMinValue(1);
    }

    public static OptionData alias(boolean required) {
        return new OptionData(OptionType.STRING, ALIAS, "Nickname to find the song more easily", required)
                .setMaxLength(40);
    }

    public Playlist resolve(CommandInteractionPayload event, String option) {
        var value = Interactions.requireString(event, option);
        return find(event, value)
                .orElseThrow(() -> new UserFacingException("Playlist " + value + " não encontrada."));
    }

    /** Índice (base 0) a partir do número digitado pelo usuário (base 1). */
    public static int trackIndex(CommandInteractionPayload event) {
        return Interactions.integer(event, TRACK) - 1;
    }

    public void autocomplete(CommandAutoCompleteInteractionEvent event) {
        var focused = event.getFocusedOption();
        var typed = focused.getValue().toLowerCase(Locale.ROOT);
        if (focused.getName().equals(TRACK)) {
            var source = Optional.ofNullable(Interactions.string(event, PLAYLIST)).or(() -> Optional.ofNullable(Interactions.string(event, FROM)));
            var tracks = source.flatMap(value -> find(event, value)).map(Playlist::tracks).orElse(List.of());
            event.replyChoices(IntStream.range(0, tracks.size())
                    .filter(i -> tracks.get(i).displayName().toLowerCase(Locale.ROOT).contains(typed) || String.valueOf(i + 1).startsWith(typed))
                    .limit(MAX_CHOICES)
                    .mapToObj(i -> new Command.Choice(Format.truncate((i + 1) + ". " + tracks.get(i).displayName(), 100), i + 1))
                    .toList()).queue();
            return;
        }
        long userId = event.getUser().getIdLong();
        event.replyChoices(visible(event).stream()
                .filter(playlist -> playlist.name().toLowerCase(Locale.ROOT).contains(typed))
                .limit(MAX_CHOICES)
                .map(playlist -> new Command.Choice(Format.truncate(label(playlist, userId), 100), playlist.id()))
                .toList()).queue();
    }

    private Optional<Playlist> find(Interaction event, String idOrName) {
        var playlists = visible(event);
        return playlists.stream().filter(playlist -> playlist.id().equals(idOrName)).findFirst()
                .or(() -> playlists.stream().filter(playlist -> playlist.name().equalsIgnoreCase(idOrName.trim())).findFirst());
    }

    private String label(Playlist playlist, long userId) {
        if (playlist.ownerId() == userId || playlist.scope() == PlaylistScope.SERVER) {
            return playlist.label();
        }
        return playlist.label() + " · de " + directory.userName(playlist.ownerId());
    }

    private List<Playlist> visible(Interaction event) {
        return playlistService.browsableBy(event.getUser().getIdLong(), Interactions.guildId(event));
    }
}
