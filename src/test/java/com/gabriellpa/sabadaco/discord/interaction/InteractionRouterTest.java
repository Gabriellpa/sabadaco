package com.gabriellpa.sabadaco.discord.interaction;

import com.gabriellpa.sabadaco.UserFacingException;
import com.gabriellpa.sabadaco.discord.DiscordDirectory;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InteractionRouterTest {

    private final List<String> calls = new CopyOnWriteArrayList<>();
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    private final SlashCommand ping = new SlashCommand() {
        @Override
        public SlashCommandData definition() {
            return Commands.slash("ping", "ping");
        }

        @Override
        public void handle(SlashCommandInteractionEvent event) {
            calls.add("ping");
        }
    };

    private final SlashCommand failing = new SlashCommand() {
        @Override
        public SlashCommandData definition() {
            return Commands.slash("boom", "boom");
        }

        @Override
        public void handle(SlashCommandInteractionEvent event) {
            throw new UserFacingException("Entre em um canal de voz primeiro.");
        }
    };

    private final Subcommand create = new Subcommand() {
        @Override
        public String parent() {
            return "playlist";
        }

        @Override
        public SubcommandData definition() {
            return new SubcommandData("create", "create");
        }

        @Override
        public void handle(SlashCommandInteractionEvent event) {
            calls.add("playlist create");
        }
    };

    private final ComponentHandler buttons = new ComponentHandler() {
        @Override
        public String prefix() {
            return "player";
        }

        @Override
        public void onButton(ButtonInteractionEvent event, String action, String payload) {
            calls.add("button " + action + " " + payload);
        }
    };

    private final InteractionRouter router = new InteractionRouter(List.of(ping, failing), List.of(create), List.of(buttons),
            meterRegistry, mock(DiscordDirectory.class));

    @Test
    void dispatchesByFullCommandName() {
        router.onSlashCommandInteraction(slash("ping"));
        router.onSlashCommandInteraction(slash("playlist create"));

        await().untilAsserted(() -> assertThat(calls).containsExactlyInAnyOrder("ping", "playlist create"));
        await().untilAsserted(() -> assertThat(meterRegistry.find("sabadaco.commands").tag("outcome", "success").timers()).hasSize(2));
    }

    @Test
    void userFacingErrorsBecomeEphemeralReplies() {
        var event = slash("boom");
        var action = mock(ReplyCallbackAction.class);
        when(event.reply(anyString())).thenReturn(action);
        when(action.setEphemeral(anyBoolean())).thenReturn(action);

        router.onSlashCommandInteraction(event);

        verify(event, timeout(2000)).reply("❌ Entre em um canal de voz primeiro.");
        verify(action, timeout(2000)).setEphemeral(true);
        await().untilAsserted(() -> assertThat(meterRegistry.find("sabadaco.commands").tag("outcome", "user_error").timer()).isNotNull());
    }

    @Test
    void dispatchesComponentsByPrefixKeepingColonsInPayload() {
        var event = mock(ButtonInteractionEvent.class);
        when(event.getComponentId()).thenReturn("player:play:https://youtu.be/x");
        when(event.getUser()).thenReturn(mock(User.class));

        router.onButtonInteraction(event);

        await().untilAsserted(() -> assertThat(calls).containsExactly("button play https://youtu.be/x"));
    }

    @Test
    void duplicateHandlersFailFast() {
        assertThatThrownBy(() -> new InteractionRouter(List.of(ping, ping), List.of(), List.of(),
                meterRegistry, mock(DiscordDirectory.class))).isInstanceOf(IllegalStateException.class);
    }

    private static SlashCommandInteractionEvent slash(String fullName) {
        var event = mock(SlashCommandInteractionEvent.class);
        when(event.getFullCommandName()).thenReturn(fullName);
        when(event.getUser()).thenReturn(mock(User.class));
        return event;
    }
}
