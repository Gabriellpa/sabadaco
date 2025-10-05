package com.gabriellpa.sabadaco.bot.discord.listeners;

import com.gabriellpa.sabadaco.bot.discord.commands.CommandExecutor;
import com.gabriellpa.sabadaco.bot.discord.commands.CommandKey;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class SlashCommandListener extends ListenerAdapter {

    private final CommandExecutor executor;

    public SlashCommandListener(CommandExecutor executor) {
        this.executor = executor;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        try {
            CommandKey key = new CommandKey(event.getName(), event.getSubcommandName());
            executor.execute(key, event);
        } catch (Exception e) {
           log.error(e.getMessage(), e);
        }
    }
}