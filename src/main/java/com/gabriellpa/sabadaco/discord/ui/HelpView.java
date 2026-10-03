package com.gabriellpa.sabadaco.discord.ui;

import com.gabriellpa.sabadaco.discord.interaction.CustomId;
import com.gabriellpa.sabadaco.discord.interaction.HelpCatalog.Entry;
import net.dv8tion.jda.api.components.MessageTopLevelComponent;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.entities.emoji.Emoji;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Telas do {@code /help}: visão geral por categoria (com menu para escolher um comando) e o detalhe
 * de um comando com exemplos.
 */
public final class HelpView {

    public static final String PREFIX = "help";
    private static final int ACCENT = 0x5865F2;
    /** Limite de opções de um select no Discord. */
    private static final int MAX_OPTIONS = 25;

    private HelpView() {
    }

    public static List<MessageTopLevelComponent> overview(List<Entry> entries) {
        var byCategory = entries.stream().collect(Collectors.groupingBy(
                entry -> entry.help().category(), LinkedHashMap::new, Collectors.toList()));
        var text = new StringBuilder("### 📖 Comandos do Sabadaço\n");
        byCategory.forEach((category, commands) -> {
            text.append("\n**").append(category.label()).append("**\n");
            commands.forEach(entry -> text.append("`").append(entry.display()).append("` ")
                    .append(entry.help().summary()).append('\n'));
        });

        var menu = StringSelectMenu.create(CustomId.of(PREFIX, "show")).setPlaceholder("🔍 Ver exemplos de um comando…");
        entries.stream().limit(MAX_OPTIONS).forEach(entry -> menu.addOption(
                entry.display(), entry.key(), Format.truncate(entry.help().summary(), 100)));

        return List.of(Container.of(
                TextDisplay.of(text.toString()),
                Separator.createDivider(Separator.Spacing.SMALL),
                TextDisplay.of("-# Em português os comandos aparecem traduzidos (ex.: `/play` é `/tocar`). Use `/help comando` para ir direto."),
                ActionRow.of(menu.build())
        ).withAccentColor(ACCENT));
    }

    public static List<MessageTopLevelComponent> detail(Entry entry) {
        var help = entry.help();
        var text = new StringBuilder("### %s\n-# %s · em português: `%s`\n%s\n".formatted(
                entry.display(), help.category().label(), entry.ptName(), help.summary()));
        text.append("\n**Exemplos**\n");
        help.examples().forEach(example -> text.append("```\n").append(example.command()).append("\n```")
                .append(example.explanation()).append('\n'));
        if (!help.tips().isEmpty()) {
            text.append("\n**Dicas**\n");
            help.tips().forEach(tip -> text.append("- ").append(tip).append('\n'));
        }
        return List.of(Container.of(
                TextDisplay.of(text.toString()),
                ActionRow.of(Button.secondary(CustomId.of(PREFIX, "home"), "Todos os comandos").withEmoji(Emoji.fromUnicode("◀️")))
        ).withAccentColor(ACCENT));
    }
}
