package com.gabriellpa.sabadaco.discord.ui;

import com.gabriellpa.sabadaco.discord.interaction.CustomId;
import com.gabriellpa.sabadaco.music.TrackSummary;
import net.dv8tion.jda.api.components.MessageTopLevelComponent;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.entities.emoji.Emoji;

import java.util.ArrayList;
import java.util.List;

/**
 * Resultados de busca: cada resultado com botão de tocar e um select para salvar em playlist.
 */
public final class SearchView {

    public static final String PREFIX = "search";
    /** customId e valores de select têm limite de 100 caracteres. */
    private static final int MAX_URI = 80;

    private SearchView() {
    }

    public static List<MessageTopLevelComponent> render(String query, List<TrackSummary> results) {
        if (results.isEmpty()) {
            return List.of(Container.of(TextDisplay.of("### 🔎 Nada encontrado para *" + Format.escape(query) + "*")));
        }
        var children = new ArrayList<ContainerChildComponent>();
        children.add(TextDisplay.of("### 🔎 Resultados para *" + Format.escape(query) + "*"));
        var saveMenu = StringSelectMenu.create(CustomId.of(PREFIX, "save")).setPlaceholder("💾 Salvar em uma playlist…");
        for (int i = 0; i < results.size(); i++) {
            var track = results.get(i);
            if (track.uri() == null || track.uri().length() > MAX_URI) {
                continue;
            }
            children.add(Section.of(
                    Button.primary(CustomId.of(PREFIX, "play", track.uri()), Emoji.fromUnicode("▶️")),
                    TextDisplay.of("**%d.** %s\n-# %s · %s".formatted(i + 1, Format.link(track, 80),
                            Format.escape(track.author()), Format.trackDuration(track)))));
            saveMenu.addOption(Format.truncate((i + 1) + ". " + track.title(), 100), track.uri());
        }
        children.add(Separator.createDivider(Separator.Spacing.SMALL));
        children.add(ActionRow.of(saveMenu.build()));
        return List.of(Container.of(children));
    }
}
