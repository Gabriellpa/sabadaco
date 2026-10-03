package com.gabriellpa.sabadaco.discord.ui;

import net.dv8tion.jda.api.components.MessageTopLevelComponent;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditData;

import java.util.List;

/**
 * Monta mensagens Components V2 sem notificar ninguém pelas menções exibidas.
 */
public final class Messages {

    private Messages() {
    }

    public static MessageCreateData create(List<? extends MessageTopLevelComponent> components) {
        return new MessageCreateBuilder()
                .useComponentsV2()
                .setComponents(components)
                .setAllowedMentions(List.of())
                .build();
    }

    public static MessageEditData edit(List<? extends MessageTopLevelComponent> components) {
        return new MessageEditBuilder()
                .useComponentsV2()
                .setComponents(components)
                .setAllowedMentions(List.of())
                .build();
    }

    public static MessageCreateData text(String content) {
        return new MessageCreateBuilder()
                .setContent(content)
                .setAllowedMentions(List.of())
                .setSuppressEmbeds(true)
                .build();
    }
}
