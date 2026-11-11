package com.gabriellpa.sabadaco.discord;

import com.gabriellpa.sabadaco.stream.StreamService;
import com.gabriellpa.sabadaco.stream.StreamSession;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.unions.AudioChannelUnion;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StreamVoiceListenerTest {

    private static final long GUILD = 10;
    private static final long VOICE = 20;
    private static final long TEXT = 30;
    private static final long HOST = 1;

    private final StreamService streams = mock(StreamService.class);
    private final StreamVoiceListener listener = new StreamVoiceListener(streams);

    @Test
    void leavingTheVoiceChannelEndsTheStreamAndAnnouncesIt() {
        var text = mock(GuildMessageChannel.class);
        var action = mock(MessageCreateAction.class);
        when(text.canTalk()).thenReturn(true);
        when(text.sendMessage(contains("Valorant"))).thenReturn(action);
        var event = event(true, false, text);
        var stream = new StreamSession(5, "Valorant", GUILD, VOICE, TEXT, HOST, "gabriel", Instant.EPOCH);
        when(streams.leftVoiceChannel(GUILD, HOST, VOICE)).thenReturn(List.of(stream));

        listener.onGuildVoiceUpdate(event);

        verify(action, timeout(2000)).queue();
    }

    @Test
    void joiningAChannelOrBotsAreIgnored() {
        listener.onGuildVoiceUpdate(event(false, false, null));
        listener.onGuildVoiceUpdate(event(true, true, null));

        verify(streams, after(300).never()).leftVoiceChannel(anyLong(), anyLong(), anyLong());
    }

    private static GuildVoiceUpdateEvent event(boolean left, boolean bot, GuildMessageChannel text) {
        var event = mock(GuildVoiceUpdateEvent.class);
        var guild = mock(Guild.class);
        var member = mock(Member.class);
        var user = mock(User.class);
        var channel = mock(AudioChannelUnion.class);
        when(guild.getIdLong()).thenReturn(GUILD);
        when(guild.getChannelById(GuildMessageChannel.class, TEXT)).thenReturn(text);
        when(member.getIdLong()).thenReturn(HOST);
        when(member.getUser()).thenReturn(user);
        when(user.isBot()).thenReturn(bot);
        when(channel.getIdLong()).thenReturn(VOICE);
        when(event.getGuild()).thenReturn(guild);
        when(event.getMember()).thenReturn(member);
        when(event.getChannelLeft()).thenReturn(left ? channel : null);
        return event;
    }
}
