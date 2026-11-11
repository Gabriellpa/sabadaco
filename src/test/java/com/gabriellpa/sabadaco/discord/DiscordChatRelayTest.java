package com.gabriellpa.sabadaco.discord;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DiscordChatRelayTest {

    @Test
    void webhookUsernameFollowsDiscordRules() {
        assertThat(DiscordChatRelay.username("gabriel")).isEqualTo("gabriel (stream)");
        assertThat(DiscordChatRelay.username("Discord Fan")).isEqualTo("d1scord Fan (stream)");
        assertThat(DiscordChatRelay.username(" ")).isEqualTo("Alguém (stream)");
        assertThat(DiscordChatRelay.username("x".repeat(100))).hasSize(DiscordChatRelay.MAX_USERNAME);
    }
}
