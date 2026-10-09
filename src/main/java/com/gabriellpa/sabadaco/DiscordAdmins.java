package com.gabriellpa.sabadaco;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Usuários do Discord com poder de admin no bot (veem e tocam a playlist de qualquer pessoa).
 * Configurado por {@code sabadaco.discord-admins} (ids separados por vírgula).
 */
@Component
public class DiscordAdmins {

    private final Set<Long> ids;

    @Autowired
    public DiscordAdmins(@Value("${sabadaco.discord-admins:}") String ids) {
        this.ids = Arrays.stream(ids.split(","))
                .map(String::trim)
                .filter(id -> !id.isEmpty())
                .map(Long::valueOf)
                .collect(Collectors.toUnmodifiableSet());
    }

    public static DiscordAdmins of(long... ids) {
        return new DiscordAdmins(Arrays.stream(ids).mapToObj(String::valueOf).collect(Collectors.joining(",")));
    }

    public boolean isAdmin(long userId) {
        return ids.contains(userId);
    }
}
