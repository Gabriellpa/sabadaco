package com.gabriellpa.sabadaco.discord.ui;

import com.gabriellpa.sabadaco.discord.interaction.CustomId;
import com.gabriellpa.sabadaco.music.LoopMode;
import com.gabriellpa.sabadaco.music.PlayerSnapshot;
import net.dv8tion.jda.api.components.MessageTopLevelComponent;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.thumbnail.Thumbnail;
import net.dv8tion.jda.api.entities.emoji.Emoji;

import java.util.ArrayList;
import java.util.List;

/**
 * Painel do player (Components V2): capa, título, progresso, fila e botões de controle.
 */
public final class PlayerPanel {

    public static final String PREFIX = "player";

    private static final int PLAYING = 0x1DB954;
    private static final int PAUSED = 0xF1C40F;
    private static final int IDLE = 0x5865F2;

    private PlayerPanel() {
    }

    public static List<MessageTopLevelComponent> render(PlayerSnapshot snapshot) {
        if (snapshot == null || !snapshot.playing()) {
            return List.of(Container.of(
                    TextDisplay.of("### 💤 Nada tocando\nUse `/play` ou `/playlist play` para começar."),
                    ActionRow.of(button("queue", "📜", "Fila"))
            ).withAccentColor(IDLE));
        }

        var current = snapshot.current();
        var track = current.track();
        var origin = current.fromPlaylist() ? " · 📜 playlist **" + Format.escape(current.playlistName()) + "**" : "";
        var description = TextDisplay.of("""
                ### %s %s
                %s
                %s `%s / %s`
                -# Pedida por %s%s""".formatted(
                snapshot.paused() ? "⏸️" : "🎶",
                Format.link(track, 80),
                Format.escape(track.author()),
                Format.progressBar(snapshot.positionMs(), track.durationMs()),
                Format.duration(snapshot.positionMs()),
                Format.trackDuration(track),
                Format.mention(current.requesterId()),
                origin));

        var children = new ArrayList<ContainerChildComponent>();
        children.add(track.artworkUrl() == null ? description : Section.of(Thumbnail.fromUrl(track.artworkUrl()), description));
        children.add(Separator.createDivider(Separator.Spacing.SMALL));
        children.add(TextDisplay.of("-# 🔊 %d%% · 🔁 %s · 📜 %d na fila · 📦 %s transmitidos".formatted(
                snapshot.volume(), loopLabel(snapshot.loopMode()), snapshot.queueSize(),
                Format.bytes(snapshot.currentTrackBytes()))));
        children.add(ActionRow.of(
                snapshot.paused()
                        ? Button.success(CustomId.of(PREFIX, "pause"), "Continuar").withEmoji(Emoji.fromUnicode("▶️"))
                        : button("pause", "⏸️", "Pausar"),
                button("skip", "⏭️", "Pular"),
                Button.danger(CustomId.of(PREFIX, "stop"), "Parar").withEmoji(Emoji.fromUnicode("⏹️")),
                button("shuffle", "🔀", "Embaralhar"),
                button("loop", "🔁", "Loop")));
        children.add(ActionRow.of(
                button("voldown", "🔉", "-10"),
                button("volup", "🔊", "+10"),
                button("queue", "📜", "Fila")));

        return List.of(Container.of(children).withAccentColor(snapshot.paused() ? PAUSED : PLAYING));
    }

    public static String loopLabel(LoopMode mode) {
        return switch (mode) {
            case OFF -> "loop desligado";
            case TRACK -> "repetindo a música";
            case QUEUE -> "repetindo a fila";
        };
    }

    private static Button button(String action, String emoji, String label) {
        return Button.secondary(CustomId.of(PREFIX, action), label).withEmoji(Emoji.fromUnicode(emoji));
    }
}
