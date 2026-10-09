package com.gabriellpa.sabadaco.discord.interaction;

import com.gabriellpa.sabadaco.discord.DiscordDirectory;
import com.gabriellpa.sabadaco.discord.command.PlayCommand;
import com.gabriellpa.sabadaco.discord.ui.HelpView;
import com.gabriellpa.sabadaco.discord.ui.PlayerPanelUpdater;
import com.gabriellpa.sabadaco.music.MusicService;
import com.gabriellpa.sabadaco.music.SearchService;
import com.gabriellpa.sabadaco.playlist.PlaylistService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.assertj.core.api.Assertions.assertThat;

/** Garante que todo comando registrado tem ajuda útil e que o /help encontra pelo nome em inglês ou português. */
@SpringJUnitConfig
class HelpCatalogTest {

    @Configuration
    @ComponentScan(basePackageClasses = PlayCommand.class)
    @Import(HelpCatalog.class)
    static class Config {
    }

    @MockitoBean
    MusicService musicService;
    @MockitoBean
    SearchService searchService;
    @MockitoBean
    PlaylistService playlistService;
    @MockitoBean
    DiscordDirectory directory;
    @MockitoBean
    PlayerPanelUpdater panels;

    @Autowired
    HelpCatalog catalog;

    @Test
    void everyCommandHasSummaryAndAtLeastOneExampleUsingItsOwnName() {
        var entries = catalog.entries();

        assertThat(entries).hasSize(21);
        assertThat(entries).allSatisfy(entry -> {
            assertThat(entry.help().summary()).isNotBlank();
            assertThat(entry.help().examples()).isNotEmpty()
                    .allSatisfy(example -> assertThat(example.command()).startsWith(entry.display()));
        });
    }

    @Test
    void findsCommandsByEnglishOrPortugueseName() {
        assertThat(catalog.find("/play")).get().extracting(HelpCatalog.Entry::ptName).isEqualTo("/tocar");
        assertThat(catalog.find("tocar")).get().extracting(HelpCatalog.Entry::key).isEqualTo("play");
        assertThat(catalog.find("playlist move")).get().extracting(HelpCatalog.Entry::ptName).isEqualTo("/playlist mover");
        assertThat(catalog.find("nada")).isEmpty();
    }

    @Test
    void overviewFitsDiscordLimits() {
        var overview = HelpView.overview(catalog.entries());

        assertThat(overview).hasSize(1);
        catalog.entries().forEach(entry -> assertThat(HelpView.detail(entry)).hasSize(1));
    }
}
