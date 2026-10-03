package com.gabriellpa.sabadaco.discord.command.playlist;

import com.gabriellpa.sabadaco.discord.interaction.CommandHelp.Category;
import com.gabriellpa.sabadaco.discord.interaction.CommandHelp;
import com.gabriellpa.sabadaco.discord.interaction.Interactions;
import com.gabriellpa.sabadaco.discord.ui.Format;
import com.gabriellpa.sabadaco.playlist.PlaylistScope;
import com.gabriellpa.sabadaco.playlist.PlaylistService;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.gabriellpa.sabadaco.discord.interaction.CommandHelp.example;

@Component
class CreatePlaylist extends PlaylistSubcommand {

    CreatePlaylist(PlaylistService playlists, PlaylistOptions options) {
        super(playlists, options);
    }

    @Override
    public SubcommandData definition() {
        return new SubcommandData("create", "Creates a playlist")
                .addOptions(
                        new OptionData(OptionType.STRING, "name", "Playlist name", true).setMaxLength(50),
                        new OptionData(OptionType.STRING, "scope", "Where it applies (default: this server only)", false)
                                .addChoice("This server only", PlaylistScope.GUILD.name())
                                .addChoice("Global (all servers)", PlaylistScope.GLOBAL.name()));
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

    @Override
    public CommandHelp help() {
        return CommandHelp.of(Category.PLAYLIST, "Cria uma playlist sua.",
                List.of(
                        example("/playlist create Clássicos do Sabadaço", "Cria só para este servidor (padrão)."),
                        example("/playlist create Favoritas scope: Global", "Cria uma playlist que vale em todos os servidores.")),
                "O nome não pode se repetir entre as suas playlists do mesmo escopo.");
    }
}
