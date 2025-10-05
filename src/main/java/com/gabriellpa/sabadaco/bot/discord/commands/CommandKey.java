package com.gabriellpa.sabadaco.bot.discord.commands;

public record CommandKey(String name, String subcommand) {

    public CommandKey(String name) {
        this(name, null);
    }
}
