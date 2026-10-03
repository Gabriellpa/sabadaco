package com.gabriellpa.sabadaco.discord.command.playlist;

import com.gabriellpa.sabadaco.discord.interaction.Interactions;
import com.gabriellpa.sabadaco.discord.ui.Format;
import com.gabriellpa.sabadaco.playlist.PlaylistScope;
import com.gabriellpa.sabadaco.playlist.PlaylistService;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.springframework.stereotype.Component;

@Component
class CreatePlaylist extends PlaylistSubcommand {

    CreatePlaylist(PlaylistService playlists, PlaylistOptions options) {
        super(playlists, options);
    }

    @Override
    public SubcommandData definition() {
        return new SubcommandData("create", "Cria uma playlist")
                .addOptions(
                        new OptionData(OptionType.STRING, "name", "Nome da playlist", true).setMaxLength(50),
                        new OptionData(OptionType.STRING, "scope", "Onde ela vale (padrão: só neste servidor)", false)
                                .addChoice("Só neste servidor", PlaylistScope.GUILD.name())
                                .addChoice("Global (todos os servidores)", PlaylistScope.GLOBAL.name()));
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        var scope = Interactions.string(event, "scope");
        var playlist = playlists.create(event.getUser().getIdLong(), Interactions.guildId(event),
                Interactions.requireString(event, "name"), scope == null ? null : PlaylistScope.valueOf(scope));
        event.reply("📜 Playlist **%s** criada (%s). Adicione músicas com `/playlist add` ou pelo 💾 da `/search`."
                        .formatted(Format.escape(playlist.name()),
                                playlist.scope() == PlaylistScope.GLOBAL ? "global" : "só neste servidor"))
                .setEphemeral(true).queue();
    }
}
