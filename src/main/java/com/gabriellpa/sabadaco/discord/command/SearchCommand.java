package com.gabriellpa.sabadaco.discord.command;

import com.gabriellpa.sabadaco.discord.interaction.CommandHelp.Category;
import com.gabriellpa.sabadaco.discord.interaction.CommandHelp;
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

import java.util.List;

import static com.gabriellpa.sabadaco.discord.interaction.CommandHelp.example;

/** {@code /search}: modo busca, com botões para tocar e select para salvar em playlist. */
@Component
@RequiredArgsConstructor
public class SearchCommand implements SlashCommand {

    private static final int RESULTS = 5;

    private final SearchService searchService;

    @Override
    public SlashCommandData definition() {
        return Commands.slash("search", "Searches songs on YouTube")
                .addOption(OptionType.STRING, "query", "What to search for", true);
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        var query = Interactions.requireString(event, "query");
        event.deferReply(true).queue();
        var results = searchService.search(query, RESULTS);
        event.getHook().sendMessage(Messages.create(SearchView.render(query, results))).queue();
    }

    @Override
    public CommandHelp help() {
        return CommandHelp.of(Category.MUSIC, "Mostra 5 resultados para você escolher.",
                List.of(
                        example("/search kasino gilberto barros", "Clique em ▶️ para tocar um resultado."),
                        example("/search kasino sabadaço", "Use o menu 💾 para salvar um resultado numa playlist: abre um formulário para escolher a playlist e dar um apelido.")),
                "A resposta só aparece para você.");
    }
}
