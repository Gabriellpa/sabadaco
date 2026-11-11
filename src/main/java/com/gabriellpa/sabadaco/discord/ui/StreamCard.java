package com.gabriellpa.sabadaco.discord.ui;

import com.gabriellpa.sabadaco.discord.interaction.CustomId;
import com.gabriellpa.sabadaco.stream.StreamSession;
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

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Card da stream no canal de texto (Components V2): ao vivo, com o botão "Assistir", e depois encerrada.
 * As menções aparecem, mas não notificam ({@link Messages} não permite menções).
 */
public final class StreamCard {

    public static final String PREFIX = "stream";
    public static final String JOIN = "join";

    private static final int LIVE = 0xE53935;
    private static final int ENDED = 0x4F545C;

    private StreamCard() {
    }

    public static List<MessageTopLevelComponent> live(StreamSession stream, String avatarUrl) {
        var header = TextDisplay.of("""
                ### 🔴 AO VIVO · %s
                %s está transmitindo a tela em <#%d>
                -# Começou <t:%d:R>""".formatted(
                Format.escape(stream.title()),
                Format.mention(stream.hostUserId()),
                stream.voiceChannelId(),
                stream.startedAt().getEpochSecond()));

        var children = new ArrayList<ContainerChildComponent>();
        children.add(withAvatar(header, avatarUrl));
        children.add(Separator.createDivider(Separator.Spacing.SMALL));
        children.add(TextDisplay.of("-# 🔒 Só quem está em <#%d> pode assistir. O link e o código chegam na sua DM e valem 5 min."
                .formatted(stream.voiceChannelId())));
        children.add(ActionRow.of(
                Button.success(CustomId.of(PREFIX, JOIN, stream.id()), "Assistir").withEmoji(Emoji.fromUnicode("📺"))));
        return List.of(Container.of(children).withAccentColor(LIVE));
    }

    /** @param reason por que terminou, ex.: "encerrada por quem transmitia" */
    public static List<MessageTopLevelComponent> ended(StreamSession stream, String avatarUrl, String reason, Instant endedAt) {
        var text = TextDisplay.of("""
                ### ⏹️ Stream encerrada · %s
                %s transmitiu em <#%d> por %s
                -# %s <t:%d:R>""".formatted(
                Format.escape(stream.title()),
                Format.mention(stream.hostUserId()),
                stream.voiceChannelId(),
                Format.duration(Duration.between(stream.startedAt(), endedAt).toMillis()),
                capitalize(reason),
                endedAt.getEpochSecond()));
        return List.of(Container.of(withAvatar(text, avatarUrl)).withAccentColor(ENDED));
    }

    private static ContainerChildComponent withAvatar(TextDisplay text, String avatarUrl) {
        return avatarUrl == null ? text : Section.of(Thumbnail.fromUrl(avatarUrl), text);
    }

    private static String capitalize(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
