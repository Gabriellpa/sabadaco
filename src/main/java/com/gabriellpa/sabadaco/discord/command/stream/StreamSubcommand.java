package com.gabriellpa.sabadaco.discord.command.stream;

import com.gabriellpa.sabadaco.discord.interaction.Subcommand;
import com.gabriellpa.sabadaco.stream.StreamService;

/** Base dos subcomandos de {@code /stream}. */
abstract class StreamSubcommand implements Subcommand {

    protected final StreamService streams;

    protected StreamSubcommand(StreamService streams) {
        this.streams = streams;
    }

    @Override
    public String parent() {
        return StreamCommandGroup.NAME;
    }
}
