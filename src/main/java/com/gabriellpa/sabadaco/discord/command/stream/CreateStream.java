package com.gabriellpa.sabadaco.discord.command.stream;

import com.gabriellpa.sabadaco.discord.interaction.CommandHelp;
import com.gabriellpa.sabadaco.discord.interaction.CommandHelp.Category;
import com.gabriellpa.sabadaco.discord.interaction.Interactions;
import com.gabriellpa.sabadaco.discord.StreamAnnouncements;
import com.gabriellpa.sabadaco.discord.StreamInvites;
import com.gabriellpa.sabadaco.discord.StreamerBadge;
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
    private final StreamAnnouncements announcements;
    private final StreamerBadge badge;

    CreateStream(StreamService streams, StreamInvites invites, StreamAnnouncements announcements, StreamerBadge badge) {
        super(streams);
        this.invites = invites;
        this.announcements = announcements;
        this.badge = badge;
    }

    @Override
    public SubcommandData definition() {
        return new SubcommandData("create", "Starts a stream of your screen in your voice channel")
                .addOptions(new OptionData(OptionType.STRING, "title", "Stream title", true).setMaxLength(100));
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        var invite = streams.create(Interactions.guildId(event), event.getChannel().getIdLong(),
                event.getUser().getIdLong(), Interactions.displayName(event), Interactions.requireString(event, "title"));
        // Card público com o botão "Assistir"; o link e o código de quem transmite vão por DM
        var avatar = event.getMember() == null ? event.getUser().getEffectiveAvatarUrl() : event.getMember().getEffectiveAvatarUrl();
        announcements.announce(event, invite.stream(), avatar);
        badge.mark(invite.stream());
        invites.deliver(event, invite, "Abra o link para compartilhar sua tela.");
    }

    @Override
    public CommandHelp help() {
        return CommandHelp.of(Category.STREAM, "Abre uma stream da sua tela no seu canal de voz.",
                List.of(example("/stream create Valorant ranqueada", "Publica no canal um card com o botão Assistir e manda por DM o seu link para transmitir.")),
                "Você precisa estar em um canal de voz. Só quem está nele pode assistir.",
                "Cada pessoa abre uma stream por vez; o canal pode ter várias ao mesmo tempo.",
                "Sair do canal de voz encerra a sua stream.",
                "Enquanto transmite, seu apelido no servidor ganha um 🔴 (se o bot puder mudar apelidos).");
    }
}
