package com.gabriellpa.sabadaco.bot.discord.commands;

import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Map;

@Component
public class CommandExecutor {

    private final Map<CommandKey, Method> commandMap;
    private final Map<CommandKey, Object> beanMap;

    public CommandExecutor(CommandRegistry registry) {
        this.commandMap = registry.getCommandMap();
        this.beanMap = registry.getBeanMap();
    }

    public void execute(CommandKey key, Object... args) throws Exception {
        if (!commandMap.containsKey(key)) {
            return;
        }
        Method method = commandMap.get(key);
        Object bean = beanMap.get(key);
        method.invoke(bean, args[0]);
    }
}