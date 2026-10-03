package com.gabriellpa.sabadaco.discord.interaction;

import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/**
 * Comando que só agrupa {@link Subcommand}s (ex.: {@code /playlist}). Define nome e descrição;
 * os subcomandos são adicionados no registro.
 */
public interface CommandGroup {

    SlashCommandData definition();
}
