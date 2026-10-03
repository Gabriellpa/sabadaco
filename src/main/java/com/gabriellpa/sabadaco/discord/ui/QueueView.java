package com.gabriellpa.sabadaco.discord.ui;

import com.gabriellpa.sabadaco.discord.interaction.CustomId;
import com.gabriellpa.sabadaco.music.PlayerSnapshot;
import com.gabriellpa.sabadaco.music.QueueEntry;
import net.dv8tion.jda.api.components.MessageTopLevelComponent;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.entities.emoji.Emoji;

import java.util.ArrayList;
import java.util.List;

/**
 * Fila paginada com remoção por select. Mostra separadamente avulsas (prioridade) e playlist.
 */
public final class QueueView {

    public static final String PREFIX = "queue";
    static final int PAGE_SIZE = 10;

    private QueueView() {
    }

    public static List<MessageTopLevelComponent> render(PlayerSnapshot snapshot, int requestedPage) {
        if (snapshot == null || (!snapshot.playing() && snapshot.queueSize() == 0)) {
            return List.of(Container.of(TextDisplay.of("### 📜 Fila vazia\nUse `/play` para adicionar músicas.")));
        }

        var upcoming = snapshot.upcoming();
        int pages = Math.max(1, (upcoming.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.clamp(requestedPage, 0, pages - 1);
        int from = page * PAGE_SIZE;
        int to = Math.min(upcoming.size(), from + PAGE_SIZE);

        var text = new StringBuilder("### 📜 Fila — %d música(s)\n".formatted(upcoming.size()));
        if (snapshot.playing()) {
            text.append("**Tocando agora:** ").append(Format.link(snapshot.current().track(), 70)).append('\n');
        }
        int prioritySize = snapshot.priorityQueue().size();
        String lastSection = null;
        for (int i = from; i < to; i++) {
            var entry = upcoming.get(i);
            var section = i < prioritySize ? "\n**🎯 Avulsas (tocam primeiro)**\n" : "\n**📜 Playlist " + Format.escape(entry.playlistName()) + "**\n";
            if (!section.equals(lastSection)) {
                text.append(section);
                lastSection = section;
            }
            text.append("`%d.` %s · `%s` · %s\n".formatted(i + 1, Format.link(entry.track(), 60),
                    Format.trackDuration(entry.track()), Format.mention(entry.requesterId())));
        }
        if (upcoming.isEmpty()) {
            text.append("\nNada depois desta música.");
        }

        var children = new ArrayList<ContainerChildComponent>();
        children.add(TextDisplay.of(text.toString()));
        children.add(Separator.createDivider(Separator.Spacing.SMALL));
        children.add(TextDisplay.of("-# Página %d de %d".formatted(page + 1, pages)));
        children.add(ActionRow.of(
                Button.secondary(CustomId.of(PREFIX, "page", page - 1), Emoji.fromUnicode("◀️")).withDisabled(page == 0),
                Button.secondary(CustomId.of(PREFIX, "page", page + 1), Emoji.fromUnicode("▶️")).withDisabled(page >= pages - 1),
                Button.secondary(CustomId.of(PREFIX, "page", page), Emoji.fromUnicode("🔄")),
                Button.danger(CustomId.of(PREFIX, "clear"), "Limpar fila").withDisabled(upcoming.isEmpty())));
        if (from < to) {
            children.add(ActionRow.of(removeMenu(upcoming, from, to, page)));
        }
        return List.of(Container.of(children));
    }

    private static StringSelectMenu removeMenu(List<QueueEntry> upcoming, int from, int to, int page) {
        var menu = StringSelectMenu.create(CustomId.of(PREFIX, "remove", page)).setPlaceholder("🗑️ Remover da fila…");
        for (int i = from; i < to; i++) {
            menu.addOption(Format.truncate((i + 1) + ". " + upcoming.get(i).track().title(), 100), String.valueOf(i));
        }
        return menu.build();
    }
}
