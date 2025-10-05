package com.gabriellpa.sabadaco.bot.discord.commands;

import com.gabriellpa.sabadaco.bot.discord.annotations.DiscordCommandComponent;
import com.gabriellpa.sabadaco.bot.discord.annotations.SlashCommand;
import com.gabriellpa.sabadaco.bot.discord.annotations.SlashOption;
import lombok.Getter;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.internal.interactions.CommandDataImpl;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

@Component
public class CommandRegistry {

    @Getter
    private final Map<CommandKey, Method> commandMap = new HashMap<>();
    @Getter
    private final Map<CommandKey, Object> beanMap = new HashMap<>();

    public CommandRegistry(ApplicationContext context) {
        context.getBeansWithAnnotation(DiscordCommandComponent.class)
                .forEach((name, bean) -> {
                    for (Method method : bean.getClass().getDeclaredMethods()) {
                        if (method.isAnnotationPresent(SlashCommand.class)) {
                            SlashCommand slashCommandAnnotation = method.getAnnotation(SlashCommand.class);
                            CommandKey key = slashCommandAnnotation.subcommand().isEmpty()
                                    ? new CommandKey(slashCommandAnnotation.name())
                                    : new CommandKey(slashCommandAnnotation.name(), slashCommandAnnotation.subcommand());
                            commandMap.put(key, method);
                            beanMap.put(key, bean);
                        }
                    }
                });
    }

    public void registerCommandsWithJDA(JDA jda) {
        commandMap.keySet().forEach(key -> {
            Method method = commandMap.get(key);
            SlashCommand annotation = method.getAnnotation(SlashCommand.class);
            CommandDataImpl commandData = (CommandDataImpl) Commands.slash(annotation.name(), annotation.description());
            if (!annotation.subcommand().isEmpty()) {
                SubcommandData sub = new SubcommandData(annotation.subcommand(), annotation.description());
                for (SlashOption option : annotation.options()) {
                    sub.addOptions(new OptionData(option.type(), option.name(), option.description(), option.required()));
                }
                commandData.addSubcommands(sub);
            } else {
                for (SlashOption option : annotation.options()) {
                    commandData.addOptions(new OptionData(option.type(), option.name(), option.description(), option.required()));
                }
            }
            jda.upsertCommand(commandData).queue();
        });
    }

}