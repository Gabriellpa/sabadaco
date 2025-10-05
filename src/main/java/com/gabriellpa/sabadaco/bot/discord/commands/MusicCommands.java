package com.gabriellpa.sabadaco.bot.discord.commands;

import com.gabriellpa.sabadaco.bot.discord.annotations.DiscordCommandComponent;
import com.gabriellpa.sabadaco.bot.discord.annotations.SlashCommand;
import com.gabriellpa.sabadaco.bot.discord.annotations.SlashOption;
import com.gabriellpa.sabadaco.service.MusicService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.entities.channel.unions.AudioChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;

import java.util.Objects;
import java.util.Optional;

@Slf4j
@DiscordCommandComponent
@RequiredArgsConstructor
public class MusicCommands {

    private final MusicService musicService;

    @SlashCommand(
            name = "music",
            description = "Comandos de música",
            subcommand = "play",
            options = {
                    @SlashOption(name = "url", description = "URL da música", type = OptionType.STRING, required = true),
                    @SlashOption(name = "volume", description = "Volume da música", type = OptionType.INTEGER, required = false)
            }
    )
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void play(SlashCommandInteractionEvent event) {
        log.info("Playing track: {}", event.getName());
        var url = Objects.requireNonNull(event.getOption("url")).getAsString();
        play(event, url);
    }

    @SlashCommand(
            name = "music",
            description = "Comandos de música",
            subcommand = "search",
            options = {
                    @SlashOption(name = "yt", description = "URL da música", type = OptionType.STRING, required = true),
                    @SlashOption(name = "volume", description = "Volume da música", type = OptionType.INTEGER, required = false)
            }
    )
    public void search(SlashCommandInteractionEvent event) {
        log.info("Playing track: {}", event.getName());
        var search = "ytsearch" + Objects.requireNonNull(event.getOption("search")).getAsString();
        play(event, search);
    }

    @SlashCommand(name = "stop", description = "Stop the music")
    public void stop() {
        log.info("Stopping music");
    }

    @SlashCommand(name = "queue", subcommand = "list", description = "List queued songs")
    public void queueList() {
        log.info("Listing queue");
    }

    @SlashCommand(name = "kassino", description = "Depois de anos sabadico nosso amigo retorna")
    public void kassino(SlashCommandInteractionEvent event) {
        play(event, "https://www.youtube.com/watch?v=LCDaw0QmQQc");
    }

    // TODO: Adicionar uma helper para esses casos.
    public AudioChannel getVoiceChannel(SlashCommandInteractionEvent event) {
        return Optional.ofNullable(event.getMember())
                .map(Member::getVoiceState)
                .map(GuildVoiceState::getChannel)
                .map(AudioChannelUnion::asVoiceChannel)
                .orElseThrow(() -> new IllegalStateException("Você precisa estar em um canal de voz para usar este comando."));
    }

    //TODO: Organizar código para reuso e melhorar estratégia de falha, possivelmente deixar no commandExecutor
    private void play(SlashCommandInteractionEvent event, String track) {
        var textChannel = event.getChannel().asTextChannel();
        try {
            var audioChannel = getVoiceChannel(event);

            musicService.play(Objects.requireNonNull(event.getGuild()), audioChannel, textChannel, track);
            textChannel.sendMessage("🎵 Tocando: " + track).queue();
        } catch (Exception exception) {
            log.info(exception.getMessage(), exception);
            textChannel.sendMessage("Falha ao carregar url: " + track);
        }
    }
}