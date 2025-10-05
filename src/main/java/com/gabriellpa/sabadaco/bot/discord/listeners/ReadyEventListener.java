package com.gabriellpa.sabadaco.bot.discord.listeners;

import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.stereotype.Component;

@Component
public class ReadyEventListener extends ListenerAdapter {
    @Override
    public void onReady(ReadyEvent event) {
        System.out.println("JDA is ready!");
    }
}