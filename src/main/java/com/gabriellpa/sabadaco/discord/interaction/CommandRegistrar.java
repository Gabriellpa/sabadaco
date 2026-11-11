package com.gabriellpa.sabadaco.discord.interaction;

import com.gabriellpa.sabadaco.discord.DiscordProperties;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.localization.LocalizationFunction;
import net.dv8tion.jda.api.interactions.commands.localization.ResourceBundleLocalizationFunction;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Registra os comandos quando o bot conecta. Registrar um comando com o mesmo nome sobrescreve o anterior, por isso
 * os subcomandos são agrupados aqui.
 *
 * <p>No servidor de dev vai tudo de uma vez ({@code updateCommands}). Globalmente não dá: com as Atividades ligadas,
 * o Discord cria o comando "Iniciar" da Atividade (Entry Point) e recusa qualquer atualização em lote que não o
 * inclua (erro 50240). O JDA 6.7 ainda não sabe montar esse tipo de comando, então os globais são atualizados um a
 * um ({@code upsertCommand}) e os que saíram do bot são apagados, sem tocar no Entry Point.
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
        if (guild != null) {
            guild.updateCommands().addCommands(commands).queue(
                    registered -> log.info("{} comandos registrados no servidor {}", registered.size(), guild.getName()),
                    error -> log.error("Falha ao registrar comandos", error));
            return;
        }
        jda.retrieveCommands().queue(existing -> {
            stale(existing, commands).forEach(command -> command.delete().queue(
                    done -> log.info("Comando /{} removido", command.getName()),
                    error -> log.error("Falha ao remover o comando /{}", command.getName(), error)));
            commands.forEach(data -> jda.upsertCommand(data).queue(null,
                    error -> log.error("Falha ao registrar o comando /{}", data.getName(), error)));
            log.info("{} comandos registrados globalmente", commands.size());
        }, error -> log.error("Falha ao ler os comandos registrados", error));
    }

    /**
     * Comandos globais que o bot não tem mais. O Entry Point da Atividade (tipo que o JDA lê como
     * {@link Command.Type#UNKNOWN}) é do Discord e fica.
     */
    static List<Command> stale(List<Command> existing, List<SlashCommandData> commands) {
        var names = commands.stream().map(SlashCommandData::getName).collect(Collectors.toSet());
        return existing.stream()
                .filter(command -> command.getType() != Command.Type.UNKNOWN)
                .filter(command -> command.getType() != Command.Type.SLASH || !names.contains(command.getName()))
                .toList();
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
