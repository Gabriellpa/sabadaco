package com.gabriellpa.sabadaco.discord.command;

import com.gabriellpa.sabadaco.discord.interaction.CommandHelp.Category;
import com.gabriellpa.sabadaco.discord.interaction.CommandHelp;
import com.gabriellpa.sabadaco.discord.interaction.Interactions;
import com.gabriellpa.sabadaco.discord.interaction.SlashCommand;
import com.gabriellpa.sabadaco.discord.ui.Format;
import com.gabriellpa.sabadaco.music.SearchService;
import com.gabriellpa.sabadaco.playlist.PlaylistService;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.gabriellpa.sabadaco.discord.interaction.CommandHelp.example;

/**
 * {@code /play}: URL ou texto. O autocomplete sugere primeiro as músicas com apelido do usuário
 * e depois resultados do YouTube.
 */
@Component
@RequiredArgsConstructor
public class PlayCommand implements SlashCommand {

    static final String QUERY = "query";
    private static final int MAX_CHOICES = 25;
    private static final int MIN_SEARCH_LENGTH = 3;
    private static final Duration DEBOUNCE = Duration.ofMillis(400);

    private final PlaybackReplies playback;
    private final SearchService searchService;
    private final PlaylistService playlistService;
    /** Contador de teclas por usuário, para o debounce do autocomplete. */
    private final Map<Long, Long> keystrokes = new ConcurrentHashMap<>();

    @Override
    public SlashCommandData definition() {
        return Commands.slash("play", "Plays a song (URL or name)")
                .addOptions(new OptionData(OptionType.STRING, QUERY, "Song URL or name", true, true));
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        playback.play(event, Interactions.requireString(event, QUERY));
    }

    @Override
    public void autocomplete(CommandAutoCompleteInteractionEvent event) {
        var typed = event.getFocusedOption().getValue().trim();
        var lower = typed.toLowerCase(Locale.ROOT);
        var choices = new ArrayList<Command.Choice>();

        playlistService.aliasedTracks(event.getUser().getIdLong(), Interactions.guildId(event)).stream()
                .filter(track -> lower.isEmpty()
                        || track.alias().toLowerCase(Locale.ROOT).contains(lower)
                        || track.title().toLowerCase(Locale.ROOT).contains(lower))
                .filter(track -> track.uri().length() <= 100)
                .limit(10)
                .forEach(track -> choices.add(new Command.Choice(Format.truncate("⭐ " + track.displayName(), 100), track.uri())));

        if (typed.length() >= MIN_SEARCH_LENGTH && !typed.startsWith("http") && isLatestAfterPause(event.getUser().getIdLong())) {
            searchService.search(typed, MAX_CHOICES - choices.size()).stream()
                    .filter(track -> track.uri() != null && track.uri().length() <= 100)
                    .forEach(track -> choices.add(new Command.Choice(
                            Format.truncate("🔎 %s — %s (%s)".formatted(track.title(), track.author(), Format.trackDuration(track)), 100),
                            track.uri())));
        }
        event.replyChoices(choices.stream().limit(MAX_CHOICES).toList()).queue();
    }

    /**
     * Debounce: o Discord manda um autocomplete por tecla. Espera um pouco e só busca no YouTube se o
     * usuário parou de digitar; pedidos antigos respondem só com os apelidos (o Discord descarta
     * respostas de pedidos que já foram substituídos).
     */
    private boolean isLatestAfterPause(long userId) {
        long keystroke = keystrokes.merge(userId, 1L, Long::sum);
        try {
            Thread.sleep(DEBOUNCE);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
        return keystrokes.get(userId) == keystroke;
    }

    @Override
    public CommandHelp help() {
        return CommandHelp.of(Category.MUSIC, "Toca uma música pelo nome ou pela URL do YouTube.",
                List.of(
                        example("/play kasino sabadaço gilberto barros", "Busca e toca. Enquanto você digita, o autocomplete sugere resultados do YouTube (🔎)."),
                        example("/play https://www.youtube.com/watch?v=RGqH4mfmtkQ", "Kasino – Can't Get Over ft. Gilberto Barros (versão Sabadaço), direto pela URL."),
                        example("/play kassino", "Se você deu o apelido \"kassino\" a uma música de playlist, ela aparece no topo com ⭐.")),
                "Você precisa estar em um canal de voz.",
                "Músicas avulsas tocam antes do restante de uma playlist em andamento: o bot avisa quando isso acontece.");
    }
}
