package com.gabriellpa.sabadaco.discord.command;

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

import java.util.ArrayList;
import java.util.Locale;

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

    private final PlaybackReplies playback;
    private final SearchService searchService;
    private final PlaylistService playlistService;

    @Override
    public SlashCommandData definition() {
        return Commands.slash("play", "Toca uma música (URL ou nome)")
                .addOptions(new OptionData(OptionType.STRING, QUERY, "URL ou nome da música", true, true));
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

        if (typed.length() >= MIN_SEARCH_LENGTH && !typed.startsWith("http")) {
            searchService.search(typed, MAX_CHOICES - choices.size()).stream()
                    .filter(track -> track.uri() != null && track.uri().length() <= 100)
                    .forEach(track -> choices.add(new Command.Choice(
                            Format.truncate("🔎 %s — %s (%s)".formatted(track.title(), track.author(), Format.trackDuration(track)), 100),
                            track.uri())));
        }
        event.replyChoices(choices.stream().limit(MAX_CHOICES).toList()).queue();
    }
}
