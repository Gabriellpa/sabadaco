package com.gabriellpa.sabadaco.discord;

import com.gabriellpa.sabadaco.stream.StreamSession;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Marca no apelido do servidor quem está transmitindo (🎥 no fim do nome) e desfaz quando a stream termina.
 *
 * <p>Precisa da permissão "Gerenciar apelidos" e de o cargo do bot estar acima do da pessoa; o Discord não deixa
 * mudar o apelido do dono do servidor. Sem isso, a stream segue normal, só sem o ícone.
 *
 * <p>O apelido original fica em memória. Se o bot reiniciar no meio de uma stream, o ícone é tirado quando a pessoa
 * sair do canal de voz (ver {@link StreamVoiceListener}).
 */
@Slf4j
@Component
public class StreamerBadge {

    static final String BADGE = " 🎥";
    static final int MAX_NICKNAME = 32;

    private record Key(long guildId, long userId) {
    }

    /** Apelido de antes do ícone; vazio = a pessoa não tinha apelido. */
    private final Map<Key, Optional<String>> originals = new ConcurrentHashMap<>();
    private final JDA jda;

    @Autowired
    public StreamerBadge(@Lazy JDA jda) {
        this.jda = jda;
    }

    /** Coloca o ícone no apelido de quem abriu a stream. */
    public void mark(StreamSession stream) {
        withMember(stream.guildId(), stream.hostUserId(), (guild, member) -> {
            var nickname = member.getNickname();
            if (nickname != null && nickname.endsWith(BADGE)) {
                return;
            }
            var badged = withBadge(member.getEffectiveName());
            originals.put(new Key(guild.getIdLong(), member.getIdLong()), Optional.ofNullable(nickname));
            guild.modifyNickname(member, badged).queue(null,
                    error -> log.debug("Não consegui pôr o ícone no apelido de {}", member.getIdLong(), error));
        });
    }

    /** Tira o ícone de quem transmitia, voltando ao apelido de antes. */
    public void unmark(StreamSession stream) {
        unmark(stream.guildId(), stream.hostUserId());
    }

    /**
     * Tira o ícone, se houver. Sem o apelido original guardado (bot reiniciou), só remove o ícone do fim.
     */
    public void unmark(long guildId, long userId) {
        var original = originals.remove(new Key(guildId, userId));
        withMember(guildId, userId, (guild, member) -> {
            var nickname = member.getNickname();
            if (nickname == null || !nickname.endsWith(BADGE)) {
                return;
            }
            var restored = original != null ? original.orElse(null) : withoutBadge(nickname);
            guild.modifyNickname(member, restored).queue(null,
                    error -> log.debug("Não consegui tirar o ícone do apelido de {}", member.getIdLong(), error));
        });
    }

    /**
     * O apelido com o ícone no fim. Se passar dos 32 caracteres do Discord, corta o nome (sem quebrar emoji nem
     * acento) para o ícone continuar aparecendo.
     */
    static String withBadge(String name) {
        var clean = name.strip();
        int room = MAX_NICKNAME - BADGE.length();
        int end = 0;
        while (end < clean.length()) {
            int next = clean.offsetByCodePoints(end, 1);
            if (next > room) {
                break;
            }
            end = next;
        }
        return clean.substring(0, end).strip() + BADGE;
    }

    /** Remove o ícone do fim; sem nada sobrando, volta a não ter apelido. */
    static String withoutBadge(String nickname) {
        var rest = nickname.endsWith(BADGE) ? nickname.substring(0, nickname.length() - BADGE.length()).strip() : nickname;
        return rest.isEmpty() ? null : rest;
    }

    private void withMember(long guildId, long userId, MemberAction action) {
        Guild guild = jda.getGuildById(guildId);
        if (guild == null) {
            return;
        }
        guild.retrieveMemberById(userId).queue(member -> {
            var self = guild.getSelfMember();
            if (member.isOwner() || !self.hasPermission(Permission.NICKNAME_MANAGE) || !self.canInteract(member)) {
                log.debug("Sem permissão para mudar o apelido de {} no servidor {}", userId, guildId);
                return;
            }
            action.apply(guild, member);
        }, error -> log.debug("Membro {} não encontrado no servidor {}", userId, guildId, error));
    }

    @FunctionalInterface
    private interface MemberAction {
        void apply(Guild guild, Member member);
    }
}
