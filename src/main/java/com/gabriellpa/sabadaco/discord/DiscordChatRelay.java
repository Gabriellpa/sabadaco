package com.gabriellpa.sabadaco.discord;

import com.gabriellpa.sabadaco.discord.ui.Format;
import com.gabriellpa.sabadaco.stream.ChatRelay;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message.MentionType;
import net.dv8tion.jda.api.entities.Webhook;
import net.dv8tion.jda.api.entities.channel.attribute.IWebhookContainer;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Leva para o canal de texto as mensagens escritas na página da stream.
 *
 * <p>Com a permissão "Gerenciar webhooks", posta por um webhook do bot com o nome e o avatar da pessoa (parece que
 * ela mesma escreveu). Sem ela, o bot posta em nome dele: "**nome** (stream): texto". Nunca menciona ninguém.
 */
@Slf4j
@Component
public class DiscordChatRelay implements ChatRelay {

    static final String WEBHOOK_NAME = "SABADACO stream";
    static final int MAX_USERNAME = 80;

    private final JDA jda;
    /** Webhook do bot por canal de texto. Também diz ao listener quais mensagens são eco do próprio bot. */
    private final Map<Long, Webhook> webhooks = new ConcurrentHashMap<>();

    @Autowired
    public DiscordChatRelay(@Lazy JDA jda) {
        this.jda = jda;
    }

    @Override
    public void postToDiscord(long guildId, long textChannelId, long userId, String displayName, String content) {
        var channel = jda.getChannelById(GuildMessageChannel.class, textChannelId);
        if (channel == null || channel.getGuild().getIdLong() != guildId || !channel.canTalk()) {
            log.debug("Chat da stream: sem acesso ao canal {}", textChannelId);
            return;
        }
        var webhook = webhookFor(channel);
        if (webhook.isPresent()) {
            webhook.get().sendMessage(message(content))
                    .setUsername(username(displayName))
                    .setAvatarUrl(avatarUrl(guildId, userId))
                    .queue(null, error -> {
                        // Webhook apagado por alguém: esquece e manda pelo bot
                        webhooks.remove(textChannelId);
                        channel.sendMessage(asBot(displayName, content)).queue();
                    });
        } else {
            channel.sendMessage(asBot(displayName, content)).queue();
        }
    }

    @Override
    public String avatarUrl(long guildId, long userId) {
        // Quem usa a página está no canal de voz, e o cache guarda os membros que estão em canais de voz
        var guild = jda.getGuildById(guildId);
        var member = guild == null ? null : guild.getMemberById(userId);
        return member == null ? null : member.getEffectiveAvatarUrl();
    }

    /** Mensagem postada por um webhook nosso (o listener não manda de volta para a página, que já mostrou). */
    public boolean isOwnWebhook(long webhookId) {
        return webhooks.values().stream().anyMatch(webhook -> webhook.getIdLong() == webhookId);
    }

    private Optional<Webhook> webhookFor(GuildMessageChannel channel) {
        if (!(channel instanceof IWebhookContainer container)
                || !channel.getGuild().getSelfMember().hasPermission(container, Permission.MANAGE_WEBHOOKS)) {
            return Optional.empty();
        }
        try {
            return Optional.of(webhooks.computeIfAbsent(channel.getIdLong(), id -> container.retrieveWebhooks().complete().stream()
                    .filter(webhook -> WEBHOOK_NAME.equals(webhook.getName()) && webhook.getOwnerAsUser() != null
                            && webhook.getOwnerAsUser().getIdLong() == jda.getSelfUser().getIdLong())
                    .findFirst()
                    .orElseGet(() -> container.createWebhook(WEBHOOK_NAME).complete())));
        } catch (RuntimeException error) {
            log.warn("Chat da stream: não consegui usar webhook no canal {}", channel.getIdLong(), error);
            return Optional.empty();
        }
    }

    private static MessageCreateData message(String content) {
        return new MessageCreateBuilder()
                .setContent(content)
                .setAllowedMentions(EnumSet.noneOf(MentionType.class))
                .build();
    }

    private static MessageCreateData asBot(String displayName, String content) {
        return message("**%s** (stream): %s".formatted(Format.escape(displayName), content));
    }

    /** O Discord recusa nomes de webhook com "discord" e com mais de 80 caracteres. */
    static String username(String displayName) {
        var name = (displayName == null || displayName.isBlank() ? "Alguém" : displayName)
                .replaceAll("(?i)discord", "d1scord") + " (stream)";
        return name.length() > MAX_USERNAME ? name.substring(0, MAX_USERNAME) : name;
    }
}
