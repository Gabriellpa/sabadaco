package com.gabriellpa.sabadaco.music;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TrackLoaderTest {

    @Test
    void urlsAndSearchPrefixesPassThrough() {
        assertThat(TrackLoader.toIdentifier(" https://www.youtube.com/watch?v=abc ")).isEqualTo("https://www.youtube.com/watch?v=abc");
        assertThat(TrackLoader.toIdentifier("scsearch:lofi")).isEqualTo("scsearch:lofi");
    }

    @Test
    void freeTextBecomesYoutubeSearch() {
        assertThat(TrackLoader.toIdentifier("kassino sabadaço")).isEqualTo("ytsearch:kassino sabadaço");
    }
}
