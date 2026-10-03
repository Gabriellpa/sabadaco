package com.gabriellpa.sabadaco.discord;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.User;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Nomes de servidores e usuários para exibição (painel admin). Usuários são lembrados quando
 * interagem com o bot, evitando chamadas extras à API do Discord.
 */
@Component
public class DiscordDirectory {

    public record GuildInfo(long id, String name) {
    }

    private final JDA jda;
    private final Map<Long, String> userNames = new ConcurrentHashMap<>();

    public DiscordDirectory(@Lazy JDA jda) {
        this.jda = jda;
    }

    public void remember(User user) {
        userNames.put(user.getIdLong(), user.getEffectiveName());
    }

    public String userName(long userId) {
        return userNames.getOrDefault(userId, String.valueOf(userId));
    }

    public String guildName(long guildId) {
        var guild = jda.getGuildById(guildId);
        return guild == null ? String.valueOf(guildId) : guild.getName();
    }

    public List<GuildInfo> guilds() {
        return jda.getGuilds().stream()
                .map(guild -> new GuildInfo(guild.getIdLong(), guild.getName()))
                .sorted(Comparator.comparing(GuildInfo::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public Map<Long, String> knownUsers() {
        return Map.copyOf(userNames);
    }
}
