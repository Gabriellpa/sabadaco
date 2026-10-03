package com.gabriellpa.sabadaco.discord.command;

import com.gabriellpa.sabadaco.discord.interaction.Interactions;
import com.gabriellpa.sabadaco.discord.interaction.SlashCommand;
import com.gabriellpa.sabadaco.discord.ui.Messages;
import com.gabriellpa.sabadaco.discord.ui.SearchView;
import com.gabriellpa.sabadaco.music.SearchService;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.springframework.stereotype.Component;

/** {@code /search}: modo busca, com botões para tocar e select para salvar em playlist. */
@Component
@RequiredArgsConstructor
public class SearchCommand implements SlashCommand {

    private static final int RESULTS = 5;

    private final SearchService searchService;

    @Override
    public SlashCommandData definition() {
        return Commands.slash("search", "Busca músicas no YouTube")
                .addOption(OptionType.STRING, "query", "O que procurar", true);
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        var query = Interactions.requireString(event, "query");
        event.deferReply(true).queue();
        var results = searchService.search(query, RESULTS);
        event.getHook().sendMessage(Messages.create(SearchView.render(query, results))).queue();
    }
}
