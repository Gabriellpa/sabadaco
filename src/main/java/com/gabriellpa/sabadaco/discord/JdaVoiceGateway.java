package com.gabriellpa.sabadaco.discord;

import com.gabriellpa.sabadaco.UserFacingException;
import com.gabriellpa.sabadaco.music.GuildPlayer;
import com.gabriellpa.sabadaco.music.VoiceGateway;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class JdaVoiceGateway implements VoiceGateway {

    private final JDA jda;

    /** {@code @Lazy} quebra o ciclo JDA → listeners → comandos → MusicService → VoiceGateway → JDA. */
    public JdaVoiceGateway(@Lazy JDA jda) {
        this.jda = jda;
    }

    @Override
    public void connect(long guildId, long voiceChannelId, GuildPlayer player) {
        var guild = guild(guildId).orElseThrow(() -> new UserFacingException("Servidor não encontrado."));
        var channel = guild.getChannelById(AudioChannel.class, voiceChannelId);
        if (channel == null) {
            throw new UserFacingException("Canal de voz não encontrado.");
        }
        var audioManager = guild.getAudioManager();
        audioManager.setSendingHandler(new AudioPlayerSendHandler(player));
        audioManager.setSelfDeafened(true);
        audioManager.openAudioConnection(channel);
    }

    @Override
    public void disconnect(long guildId) {
        guild(guildId).ifPresent(guild -> guild.getAudioManager().closeAudioConnection());
    }

    @Override
    public boolean isConnected(long guildId) {
        return guild(guildId).map(guild -> guild.getAudioManager().isConnected()).orElse(false);
    }

    private Optional<Guild> guild(long guildId) {
        return Optional.ofNullable(jda.getGuildById(guildId));
    }
}
