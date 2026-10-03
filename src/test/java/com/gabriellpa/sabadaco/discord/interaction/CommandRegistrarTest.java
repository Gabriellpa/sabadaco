package com.gabriellpa.sabadaco.discord.interaction;

import com.gabriellpa.sabadaco.discord.DiscordProperties;
import com.gabriellpa.sabadaco.discord.command.PlayCommand;
import com.gabriellpa.sabadaco.discord.ui.PlayerPanelUpdater;
import com.gabriellpa.sabadaco.music.MusicService;
import com.gabriellpa.sabadaco.music.SearchService;
import com.gabriellpa.sabadaco.playlist.PlaylistService;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.utils.data.DataObject;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** Sobe só os beans de comando reais (serviços mockados) e valida o que seria registrado no Discord. */
@SpringJUnitConfig
class CommandRegistrarTest {

    @Configuration
    @ComponentScan(basePackageClasses = PlayCommand.class)
    @Import(CommandRegistrar.class)
    static class Config {
        @Bean
        DiscordProperties discordProperties() {
            return new DiscordProperties("token", null);
        }
    }

    @MockitoBean
    MusicService musicService;
    @MockitoBean
    SearchService searchService;
    @MockitoBean
    PlaylistService playlistService;
    @MockitoBean
    PlayerPanelUpdater panels;

    @Autowired
    CommandRegistrar registrar;

    @Test
    void registersEveryCommandOnceWithSubcommandsGrouped() {
        var commands = byName();

        assertThat(commands).containsKeys("play", "search", "player", "queue", "skip", "stop", "pause", "volume", "kassino", "playlist");
        assertThat(commands.get("playlist").getSubcommands()).extracting(SubcommandData::getName)
                .containsExactlyInAnyOrder("create", "list", "show", "play", "add", "add-current", "alias", "remove", "move", "rename", "delete");
    }

    @Test
    void commandsAreGuildOnlyAndLocalizedToPortuguese() {
        var commands = byName();

        assertThat(commands.values()).allSatisfy(command ->
                assertThat(command.getContexts()).containsExactly(InteractionContextType.GUILD));
        // A tradução é aplicada quando o comando é serializado para o Discord
        var play = commands.get("play").toData().toString();
        var playlist = commands.get("playlist").toData().toString();
        assertThat(play).contains("\"" + DiscordLocale.PORTUGUESE_BRAZILIAN.getLocale() + "\":\"tocar\"");
        assertThat(playlist).contains("\"mover\"", "\"apelido\"", "\"criar\"");
    }

    /** Garante que comandos novos não fiquem sem tradução: toda descrição e toda escolha precisam de pt-BR. */
    @Test
    void everyDescriptionAndChoiceHasPortugueseTranslation() {
        var missing = new ArrayList<String>();
        registrar.commandData().forEach(command -> collectMissing(command.toData(), command.getName(), missing));

        assertThat(missing).as("chaves faltando em i18n/commands_pt_BR.properties").isEmpty();
    }

    private static void collectMissing(DataObject data, String path, List<String> missing) {
        var locale = DiscordLocale.PORTUGUESE_BRAZILIAN.getLocale();
        if (data.hasKey("description") && !hasLocale(data, "description_localizations", locale)) {
            missing.add(path + ".description");
        }
        if (data.hasKey("value") && !hasLocale(data, "name_localizations", locale)) {
            missing.add(path + ".name");
        }
        for (var child : List.of("options", "choices")) {
            if (data.hasKey(child)) {
                var array = data.getArray(child);
                for (int i = 0; i < array.length(); i++) {
                    var item = array.getObject(i);
                    collectMissing(item, path + "." + item.getString("name"), missing);
                }
            }
        }
    }

    private static boolean hasLocale(DataObject data, String key, String locale) {
        return data.hasKey(key) && !data.isNull(key) && data.getObject(key).hasKey(locale);
    }

    private Map<String, SlashCommandData> byName() {
        return registrar.commandData().stream().collect(Collectors.toMap(SlashCommandData::getName, Function.identity()));
    }
}
