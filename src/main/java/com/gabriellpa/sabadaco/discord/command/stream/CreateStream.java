package com.gabriellpa.sabadaco.discord.command.stream;

import com.gabriellpa.sabadaco.discord.interaction.CommandHelp;
import com.gabriellpa.sabadaco.discord.interaction.CommandHelp.Category;
import com.gabriellpa.sabadaco.discord.interaction.Interactions;
import com.gabriellpa.sabadaco.discord.ui.Format;
import com.gabriellpa.sabadaco.stream.StreamService;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.gabriellpa.sabadaco.discord.interaction.CommandHelp.example;

@Component
class CreateStream extends StreamSubcommand {

    private final StreamInvites invites;

    CreateStream(StreamService streams, StreamInvites invites) {
        super(streams);
        this.invites = invites;
    }

    @Override
    public SubcommandData definition() {
        return new SubcommandData("create", "Starts a stream of your screen in your voice channel")
                .addOptions(new OptionData(OptionType.STRING, "title", "Stream title", true).setMaxLength(100));
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        var member = event.getMember();
        var invite = streams.create(Interactions.guildId(event), event.getChannel().getIdLong(),
                event.getUser().getIdLong(), member == null ? event.getUser().getEffectiveName() : member.getEffectiveName(),
                Interactions.requireString(event, "title"));
        var stream = invite.stream();
        // Aviso público: quem está no canal de voz sabe que pode entrar
        event.reply("🔴 %s está ao vivo: **%s** em <#%d>. Quem está no canal pode assistir com `/stream join`."
                        .formatted(Format.mention(stream.hostUserId()), Format.escape(stream.title()), stream.voiceChannelId()))
                .queue();
        invites.deliver(event, invite, "Abra o link para compartilhar sua tela.");
    }

    @Override
    public CommandHelp help() {
        return CommandHelp.of(Category.STREAM, "Abre uma stream da sua tela no seu canal de voz.",
                List.of(example("/stream create Valorant ranqueada", "Avisa o canal e manda por DM o link e o código para você transmitir.")),
                "Você precisa estar em um canal de voz. Só quem está nele pode assistir.",
                "Cada pessoa abre uma stream por vez; o canal pode ter várias ao mesmo tempo.",
                "Sair do canal de voz encerra a sua stream.");
    }
}
