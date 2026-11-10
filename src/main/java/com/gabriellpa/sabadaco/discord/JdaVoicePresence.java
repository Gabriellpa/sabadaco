package com.gabriellpa.sabadaco.discord;

import com.gabriellpa.sabadaco.stream.VoicePresence;
import net.dv8tion.jda.api.JDA;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Canal de voz de alguém, pelo cache de voice states do JDA (intent GUILD_VOICE_STATES + MemberCachePolicy.VOICE). */
@Component
public class JdaVoicePresence implements VoicePresence {

    private final JDA jda;

    public JdaVoicePresence(@Lazy JDA jda) {
        this.jda = jda;
    }

    @Override
    public Optional<Long> voiceChannelOf(long guildId, long userId) {
        return Optional.ofNullable(jda.getGuildById(guildId))
                .map(guild -> guild.getMemberById(userId))
                .map(member -> member.getVoiceState())
                .map(state -> state.getChannel())
                .map(channel -> channel.getIdLong());
    }
}
