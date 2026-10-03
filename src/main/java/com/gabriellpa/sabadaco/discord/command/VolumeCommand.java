package com.gabriellpa.sabadaco.discord.command;

import com.gabriellpa.sabadaco.discord.interaction.CommandHelp.Category;
import com.gabriellpa.sabadaco.discord.interaction.CommandHelp;
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

import java.util.List;

import static com.gabriellpa.sabadaco.discord.interaction.CommandHelp.example;

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

    @Override
    public CommandHelp help() {
        return CommandHelp.of(Category.CONTROLS, "Ajusta o volume de 0 a 150.",
                List.of(
                        example("/volume 80", "Volume em 80%."),
                        example("/volume 150", "Volume máximo (pode distorcer).")),
                "Os botões 🔉 e 🔊 do painel mudam de 10 em 10.");
    }
}
