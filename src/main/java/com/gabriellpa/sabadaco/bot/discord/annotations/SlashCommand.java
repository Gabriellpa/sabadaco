package com.gabriellpa.sabadaco.bot.discord.annotations;

import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SlashCommand {
    String name();

    String description() default "";

    String subcommand() default "";

    SlashOption[] options() default {};

}