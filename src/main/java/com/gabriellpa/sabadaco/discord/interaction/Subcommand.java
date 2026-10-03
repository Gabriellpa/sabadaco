package com.gabriellpa.sabadaco.discord.interaction;

import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

/**
 * Subcomando ({@code /playlist create}) em sua própria classe. Os subcomandos são agrupados pelo
 * {@link #parent()} sob o {@link CommandGroup} de mesmo nome e registrados em um único comando.
 */
public interface Subcommand extends CommandHandler {

    /** Nome do {@link CommandGroup} pai, ex.: {@code "playlist"}. */
    String parent();

    SubcommandData definition();
}
