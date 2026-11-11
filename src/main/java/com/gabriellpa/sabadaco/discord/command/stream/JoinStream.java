package com.gabriellpa.sabadaco.discord.command.stream;

import com.gabriellpa.sabadaco.discord.StreamInvites;
import com.gabriellpa.sabadaco.discord.interaction.CommandHelp;
import com.gabriellpa.sabadaco.discord.interaction.CommandHelp.Category;
import com.gabriellpa.sabadaco.discord.interaction.Interactions;
import com.gabriellpa.sabadaco.discord.ui.Format;
import com.gabriellpa.sabadaco.stream.StreamService;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

import static com.gabriellpa.sabadaco.discord.interaction.CommandHelp.example;

@Component
class JoinStream extends StreamSubcommand {

    private final StreamInvites invites;

    JoinStream(StreamService streams, StreamInvites invites) {
        super(streams);
        this.invites = invites;
    }

    @Override
    public SubcommandData definition() {
        return new SubcommandData("join", "Gets a code to watch a stream in your voice channel")
                .addOptions(new OptionData(OptionType.STRING, "stream", "Stream title or who is streaming (default: the first one)", false)
                        .setAutoComplete(true));
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();
        var invite = streams.join(Interactions.guildId(event), event.getUser().getIdLong(),
                Interactions.displayName(event), Interactions.string(event, "stream"));
        invites.deliver(event, invite, "📺 **%s** de %s.".formatted(Format.escape(invite.stream().title()),
                Format.escape(invite.stream().hostName())));
    }

    /** Sugere as streams ao vivo do canal de voz de quem está digitando; o valor é o id da stream. */
    @Override
    public void autocomplete(CommandAutoCompleteInteractionEvent event) {
        var guild = event.getGuild();
        var voice = event.getMember() == null || event.getMember().getVoiceState() == null
                ? null : event.getMember().getVoiceState().getChannel();
        if (guild == null || voice == null) {
            event.replyChoices().queue();
            return;
        }
        var typed = event.getFocusedOption().getValue().toLowerCase(Locale.ROOT);
        event.replyChoices(streams.streamsIn(guild.getIdLong(), voice.getIdLong()).stream()
                        .filter(s -> s.title().toLowerCase(Locale.ROOT).contains(typed)
                                || s.hostName().toLowerCase(Locale.ROOT).contains(typed))
                        .limit(25)
                        .map(s -> new Command.Choice(Format.truncate(s.title() + " — " + s.hostName(), 100), String.valueOf(s.id())))
                        .toList())
                .queue();
    }

    @Override
    public CommandHelp help() {
        return CommandHelp.of(Category.STREAM, "Recebe por DM o link e o código para assistir a uma stream do seu canal de voz.",
                List.of(
                        example("/stream join", "A primeira stream do seu canal."),
                        example("/stream join Valorant", "Pelo título (o autocomplete mostra as streams ao vivo)."),
                        example("/stream join gabriel", "Por quem está transmitindo.")),
                "Também dá para clicar em 📺 Assistir no card da stream.",
                "O código vale 5 minutos, uma vez só e só no link que veio com ele.",
                "Lá dentro você vê todas as streams do canal, sem pedir outro código.",
                "Sair do canal de voz tira você da stream.");
    }
}
