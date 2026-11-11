package com.gabriellpa.sabadaco.discord.command.stream;

import com.gabriellpa.sabadaco.discord.StreamAnnouncements;
import com.gabriellpa.sabadaco.discord.interaction.CommandHelp;
import com.gabriellpa.sabadaco.discord.interaction.CommandHelp.Category;
import com.gabriellpa.sabadaco.discord.interaction.Interactions;
import com.gabriellpa.sabadaco.discord.ui.Format;
import com.gabriellpa.sabadaco.stream.StreamService;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.gabriellpa.sabadaco.discord.interaction.CommandHelp.example;

@Component
class EndStream extends StreamSubcommand {

    private final StreamAnnouncements announcements;

    EndStream(StreamService streams, StreamAnnouncements announcements) {
        super(streams);
        this.announcements = announcements;
    }

    @Override
    public SubcommandData definition() {
        return new SubcommandData("end", "Ends your stream");
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        var stream = streams.endOwn(Interactions.guildId(event), event.getUser().getIdLong());
        announcements.ended(stream, "encerrada por quem transmitia");
        event.reply("⏹️ Stream **%s** encerrada.".formatted(Format.escape(stream.title()))).setEphemeral(true).queue();
    }

    @Override
    public CommandHelp help() {
        return CommandHelp.of(Category.STREAM, "Encerra a sua stream.",
                List.of(example("/stream end", "Para de transmitir. Você continua podendo assistir às outras streams do canal.")),
                "Sair do canal de voz também encerra a sua stream.");
    }
}
