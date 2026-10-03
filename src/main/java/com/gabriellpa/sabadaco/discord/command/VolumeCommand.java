package com.gabriellpa.sabadaco.discord.command;

import com.gabriellpa.sabadaco.discord.interaction.Interactions;
import com.gabriellpa.sabadaco.discord.interaction.SlashCommand;
import com.gabriellpa.sabadaco.music.MusicService;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class VolumeCommand implements SlashCommand {

    private final MusicService musicService;

    @Override
    public SlashCommandData definition() {
        return Commands.slash("volume", "Adjusts the volume")
                .addOptions(new OptionData(OptionType.INTEGER, "level", "Volume from 0 to " + MusicService.MAX_VOLUME, true)
                        .setRequiredRange(0, MusicService.MAX_VOLUME));
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        int volume = musicService.setVolume(Interactions.guildId(event), Interactions.integer(event, "level"));
        event.reply("🔊 Volume em " + volume + "%.").queue();
    }
}
