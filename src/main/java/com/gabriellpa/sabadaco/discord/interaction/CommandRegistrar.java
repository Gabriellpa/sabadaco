package com.gabriellpa.sabadaco.discord.interaction;

import com.gabriellpa.sabadaco.discord.DiscordProperties;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.localization.LocalizationFunction;
import net.dv8tion.jda.api.interactions.commands.localization.ResourceBundleLocalizationFunction;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Registra todos os comandos de uma vez ({@code updateCommands}, bulk overwrite) quando o bot conecta.
 * Registrar um por um com o mesmo nome sobrescreve o anterior, por isso os subcomandos são agrupados aqui.
 */
@Slf4j
@Component
public class CommandRegistrar extends ListenerAdapter {

    private static final LocalizationFunction LOCALIZATION = ResourceBundleLocalizationFunction
            .fromBundles("i18n/commands", DiscordLocale.PORTUGUESE_BRAZILIAN)
            .build();

    private final List<SlashCommand> slashCommands;
    private final List<CommandGroup> groups;
    private final List<Subcommand> subcommands;
    private final DiscordProperties properties;

    public CommandRegistrar(List<SlashCommand> slashCommands, List<CommandGroup> groups,
                            List<Subcommand> subcommands, DiscordProperties properties) {
        this.slashCommands = slashCommands;
        this.groups = groups;
        this.subcommands = subcommands;
        this.properties = properties;
    }

    @Override
    public void onReady(ReadyEvent event) {
        var commands = commandData();
        var jda = event.getJDA();
        var guild = properties.devGuildId() == null ? null : jda.getGuildById(properties.devGuildId());
        var action = guild != null ? guild.updateCommands() : jda.updateCommands();
        action.addCommands(commands).queue(
                registered -> log.info("{} comandos registrados {}", registered.size(),
                        guild != null ? "no servidor " + guild.getName() : "globalmente"),
                error -> log.error("Falha ao registrar comandos", error));
    }

    public List<SlashCommandData> commandData() {
        var byParent = subcommands.stream().collect(Collectors.groupingBy(Subcommand::parent));
        var groupNames = groups.stream().map(group -> group.definition().getName()).toList();
        byParent.keySet().stream()
                .filter(parent -> !groupNames.contains(parent))
                .findFirst()
                .ifPresent(orphan -> {
                    throw new IllegalStateException("Subcomando sem CommandGroup: " + orphan);
                });

        var grouped = groups.stream().map(group -> {
            var data = group.definition();
            byParent.getOrDefault(data.getName(), List.of()).forEach(sub -> data.addSubcommands(sub.definition()));
            return data;
        });
        return Stream.concat(slashCommands.stream().map(SlashCommand::definition), grouped)
                .map(data -> data.setContexts(InteractionContextType.GUILD).setLocalizationFunction(LOCALIZATION))
                .toList();
    }
}
