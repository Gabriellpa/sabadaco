package com.gabriellpa.sabadaco.discord.interaction;

import com.gabriellpa.sabadaco.UserFacingException;
import com.gabriellpa.sabadaco.discord.DiscordDirectory;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Único listener de interações: despacha para o handler certo, mede tempo e trata erros em um só lugar.
 * Cada interação roda em uma virtual thread, então os handlers podem bloquear sem travar o JDA.
 */
@Slf4j
@Component
public class InteractionRouter extends ListenerAdapter {

    private static final String GENERIC_ERROR = "Algo deu errado. Tente de novo em instantes.";

    private final Map<String, CommandHandler> commands = new HashMap<>();
    private final Map<String, ComponentHandler> components = new HashMap<>();
    private final MeterRegistry meterRegistry;
    private final DiscordDirectory directory;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public InteractionRouter(List<SlashCommand> slashCommands, List<Subcommand> subcommands,
                             List<ComponentHandler> componentHandlers, MeterRegistry meterRegistry,
                             DiscordDirectory directory) {
        this.meterRegistry = meterRegistry;
        this.directory = directory;
        slashCommands.forEach(command -> register(commands, command.definition().getName(), command));
        subcommands.forEach(sub -> register(commands, sub.parent() + " " + sub.definition().getName(), sub));
        componentHandlers.forEach(handler -> register(components, handler.prefix(), handler));
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        var handler = commands.get(event.getFullCommandName());
        if (handler == null) {
            log.warn("Comando sem handler: {}", event.getFullCommandName());
            return;
        }
        executor.execute(() -> run(event, "sabadaco.commands", "command", event.getFullCommandName(), () -> handler.handle(event)));
    }

    @Override
    public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
        var handler = commands.get(event.getFullCommandName());
        if (handler == null) {
            return;
        }
        executor.execute(() -> {
            try {
                handler.autocomplete(event);
            } catch (Exception e) {
                log.debug("Falha no autocomplete de {}: {}", event.getFullCommandName(), e.getMessage());
            }
            if (!event.isAcknowledged()) {
                event.replyChoices().queue();
            }
        });
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        dispatch(event, event.getComponentId(), (handler, id) -> handler.onButton(event, id.action(), id.payload()));
    }

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        dispatch(event, event.getComponentId(), (handler, id) -> handler.onSelect(event, id.action(), id.payload()));
    }

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        dispatch(event, event.getModalId(), (handler, id) -> handler.onModal(event, id.action(), id.payload()));
    }

    private void dispatch(IReplyCallback event, String rawId, ComponentAction action) {
        var id = CustomId.parse(rawId);
        var handler = components.get(id.prefix());
        if (handler == null) {
            log.warn("Componente sem handler: {}", rawId);
            return;
        }
        executor.execute(() -> run(event, "sabadaco.components", "component", id.prefix() + ":" + id.action(),
                () -> action.accept(handler, id)));
    }

    private void run(IReplyCallback event, String metric, String tagName, String tagValue, Runnable work) {
        directory.remember(event.getUser());
        var sample = Timer.start(meterRegistry);
        var outcome = "success";
        try {
            work.run();
        } catch (UserFacingException e) {
            outcome = "user_error";
            replyError(event, e.getMessage());
        } catch (Exception e) {
            outcome = "error";
            log.error("Erro em {} {}", tagName, tagValue, e);
            replyError(event, GENERIC_ERROR);
        } finally {
            sample.stop(meterRegistry.timer(metric, tagName, tagValue, "outcome", outcome));
        }
    }

    private static void replyError(IReplyCallback event, String message) {
        var text = "❌ " + message;
        if (event.isAcknowledged()) {
            event.getHook().sendMessage(text).setEphemeral(true).queue();
        } else {
            event.reply(text).setEphemeral(true).queue();
        }
    }

    private static <T> void register(Map<String, T> map, String key, T handler) {
        if (map.putIfAbsent(key, handler) != null) {
            throw new IllegalStateException("Handler duplicado para " + key);
        }
    }

    @FunctionalInterface
    private interface ComponentAction {
        void accept(ComponentHandler handler, CustomId id);
    }
}
