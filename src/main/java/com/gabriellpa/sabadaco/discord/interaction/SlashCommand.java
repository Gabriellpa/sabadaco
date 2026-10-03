package com.gabriellpa.sabadaco.discord.interaction;

import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/**
 * Comando de primeiro nível ({@code /play}). Para criar um novo comando basta um
 * {@code @Component} implementando esta interface; registro e roteamento são automáticos.
 */
public interface SlashCommand extends CommandHandler {

    /** Definição com o builder nativo do JDA ({@code Commands.slash(...)}). */
    SlashCommandData definition();
}
