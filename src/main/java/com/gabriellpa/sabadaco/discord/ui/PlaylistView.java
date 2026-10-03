package com.gabriellpa.sabadaco.discord.ui;

import com.gabriellpa.sabadaco.discord.interaction.CustomId;
import com.gabriellpa.sabadaco.playlist.Playlist;
import com.gabriellpa.sabadaco.playlist.PlaylistScope;
import net.dv8tion.jda.api.components.MessageTopLevelComponent;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.entities.emoji.Emoji;

import java.util.List;

public final class PlaylistView {

    public static final String PREFIX = "playlist";
    private static final int MAX_LINES = 25;

    private PlaylistView() {
    }

    public static List<MessageTopLevelComponent> render(Playlist playlist) {
        var text = new StringBuilder("### 📜 %s\n-# %s · %d música(s) · %s\n".formatted(
                Format.escape(playlist.name()),
                playlist.scope() == PlaylistScope.GLOBAL ? "🌐 global" : "🏠 deste servidor",
                playlist.tracks().size(),
                Format.duration(playlist.totalDurationMs())));
        var tracks = playlist.tracks();
        for (int i = 0; i < Math.min(tracks.size(), MAX_LINES); i++) {
            var track = tracks.get(i);
            var alias = track.alias() == null ? "" : "**" + Format.escape(track.alias()) + "** — ";
            text.append("`%d.` %s%s · `%s`\n".formatted(i + 1, alias,
                    Format.escape(Format.truncate(track.title(), 70)), Format.duration(track.durationMs())));
        }
        if (tracks.size() > MAX_LINES) {
            text.append("-# … e mais ").append(tracks.size() - MAX_LINES).append(" música(s)\n");
        }
        if (tracks.isEmpty()) {
            text.append("\nVazia. Use `/playlist add` ou o 💾 da busca.");
        }
        return List.of(Container.of(
                TextDisplay.of(text.toString()),
                ActionRow.of(
                        Button.success(CustomId.of(PREFIX, "play", playlist.id()), "Tocar").withEmoji(Emoji.fromUnicode("▶️")).withDisabled(tracks.isEmpty()),
                        Button.secondary(CustomId.of(PREFIX, "shuffle", playlist.id()), "Aleatório").withEmoji(Emoji.fromUnicode("🔀")).withDisabled(tracks.isEmpty()))));
    }

    public static String list(List<Playlist> playlists) {
        if (playlists.isEmpty()) {
            return "Você ainda não tem playlists aqui. Crie com `/playlist create`.";
        }
        var text = new StringBuilder("### 📚 Suas playlists\n");
        playlists.forEach(playlist -> text.append("- **%s**%s · %d música(s)\n".formatted(
                Format.escape(playlist.name()),
                playlist.scope() == PlaylistScope.GLOBAL ? " 🌐" : "",
                playlist.tracks().size())));
        return text.toString();
    }
}
