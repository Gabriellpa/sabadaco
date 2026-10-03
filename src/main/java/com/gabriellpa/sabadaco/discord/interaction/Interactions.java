package com.gabriellpa.sabadaco.discord.interaction;

import com.gabriellpa.sabadaco.UserFacingException;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.ISnowflake;
import net.dv8tion.jda.api.interactions.Interaction;
import net.dv8tion.jda.api.interactions.commands.CommandInteractionPayload;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

import java.util.Optional;

/**
 * Helpers reaproveitados pelos comandos.
 */
public final class Interactions {

    private Interactions() {
    }

    public static long guildId(Interaction event) {
        if (event.getGuild() == null) {
            throw new UserFacingException("Este comando só funciona dentro de um servidor.");
        }
        return event.getGuild().getIdLong();
    }

    /** Canal de voz em que o usuário está; obrigatório para tocar música. */
    public static long requireVoiceChannel(Interaction event) {
        return Optional.ofNullable(event.getMember())
                .map(member -> member.getVoiceState())
                .map(GuildVoiceState::getChannel)
                .map(ISnowflake::getIdLong)
                .orElseThrow(() -> new UserFacingException("Entre em um canal de voz primeiro."));
    }

    public static String string(CommandInteractionPayload event, String option) {
        return Optional.ofNullable(event.getOption(option)).map(OptionMapping::getAsString).orElse(null);
    }

    public static String requireString(CommandInteractionPayload event, String option) {
        var value = string(event, option);
        if (value == null || value.isBlank()) {
            throw new UserFacingException("Informe " + option + ".");
        }
        return value;
    }

    public static Integer integer(CommandInteractionPayload event, String option) {
        return Optional.ofNullable(event.getOption(option)).map(OptionMapping::getAsInt).orElse(null);
    }

    public static boolean bool(CommandInteractionPayload event, String option) {
        return Optional.ofNullable(event.getOption(option)).map(OptionMapping::getAsBoolean).orElse(false);
    }
}
