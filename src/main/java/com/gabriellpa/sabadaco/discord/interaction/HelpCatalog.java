package com.gabriellpa.sabadaco.discord.interaction;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.stream.Stream;

/**
 * Lista todos os comandos (e subcomandos) com sua ajuda, para o {@code /help}.
 * Usa {@link ObjectProvider} porque o próprio {@code /help} é um comando: pedir a lista no
 * construtor criaria um ciclo (o help dependeria de si mesmo).
 */
@Component
public class HelpCatalog {

    /**
     * @param key    nome completo como o Discord envia, ex.: {@code play} ou {@code playlist move}
     * @param ptName o mesmo comando como aparece para quem usa o Discord em português
     */
    public record Entry(String key, String ptName, String description, CommandHelp help) {
        public String display() {
            return "/" + key;
        }
    }

    private static final ResourceBundle PT_BR = ResourceBundle.getBundle("i18n/commands", Locale.of("pt", "BR"));

    private final ObjectProvider<SlashCommand> slashCommands;
    private final ObjectProvider<Subcommand> subcommands;

    public HelpCatalog(ObjectProvider<SlashCommand> slashCommands, ObjectProvider<Subcommand> subcommands) {
        this.slashCommands = slashCommands;
        this.subcommands = subcommands;
    }

    public List<Entry> entries() {
        var commands = slashCommands.orderedStream().map(command -> {
            var data = command.definition();
            return new Entry(data.getName(), "/" + translate(data.getName()), data.getDescription(), command.help());
        });
        var subs = subcommands.orderedStream().map(sub -> {
            var data = sub.definition();
            var key = sub.parent() + " " + data.getName();
            return new Entry(key, "/" + translate(sub.parent()) + " " + translate(sub.parent() + "." + data.getName()),
                    data.getDescription(), sub.help());
        });
        return Stream.concat(commands, subs)
                .sorted(Comparator.comparing((Entry entry) -> entry.help().category()).thenComparing(Entry::key))
                .toList();
    }

    public Optional<Entry> find(String key) {
        var normalized = key == null ? "" : key.trim().replaceFirst("^/", "").toLowerCase(Locale.ROOT);
        return entries().stream()
                .filter(entry -> entry.key().equals(normalized) || entry.ptName().substring(1).equals(normalized))
                .findFirst();
    }

    /** Nome traduzido (chave {@code <caminho>.name} do bundle); sem tradução, o próprio nome. */
    private static String translate(String path) {
        var key = path + ".name";
        var name = path.contains(".") ? path.substring(path.lastIndexOf('.') + 1) : path;
        return PT_BR.containsKey(key) ? PT_BR.getString(key) : name;
    }
}
