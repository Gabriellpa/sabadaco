package com.gabriellpa.sabadaco.stream;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Códigos em memória. Somem ao reiniciar, o que é aceitável: duram 5 minutos.
 *
 * <p>Formato {@code ABCD-EFGH}: 8 caracteres de um alfabeto sem ambíguos (sem 0/O, 1/I/L), ~40 bits.
 * Com uso único e 5 minutos de validade, adivinhar um código por força bruta não é viável.
 */
public class InMemoryJoinCodeIssuer implements JoinCodeIssuer {

    static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    static final int LENGTH = 8;

    private final Clock clock;
    private final Duration ttl;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Pending> pending = new HashMap<>();

    private record Pending(JoinGrant grant, Instant expiresAt) {
    }

    public InMemoryJoinCodeIssuer(Clock clock, Duration ttl) {
        this.clock = clock;
        this.ttl = ttl;
    }

    @Override
    public synchronized JoinCode issue(long streamId, long guildId, long voiceChannelId, long userId, String displayName) {
        var now = clock.instant();
        pending.values().removeIf(entry -> !entry.expiresAt().isAfter(now));
        revoke(guildId, userId);
        String code;
        do {
            code = randomCode();
        } while (pending.containsKey(code));
        var expiresAt = now.plus(ttl);
        pending.put(code, new Pending(new JoinGrant(streamId, guildId, voiceChannelId, userId, displayName), expiresAt));
        return new JoinCode(format(code), expiresAt);
    }

    @Override
    public synchronized Optional<JoinGrant> redeem(String code) {
        if (code == null) {
            return Optional.empty();
        }
        var entry = pending.remove(normalize(code));
        if (entry == null || !entry.expiresAt().isAfter(clock.instant())) {
            return Optional.empty();
        }
        return Optional.of(entry.grant());
    }

    @Override
    public synchronized void revoke(long guildId, long userId) {
        pending.values().removeIf(entry -> entry.grant().guildId() == guildId && entry.grant().userId() == userId);
    }

    synchronized int size() {
        return pending.size();
    }

    private String randomCode() {
        var builder = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            builder.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return builder.toString();
    }

    /** Aceita minúsculas, espaços e hífen: quem digita não precisa acertar a formatação. */
    static String normalize(String code) {
        return code.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    private static String format(String code) {
        return code.substring(0, LENGTH / 2) + "-" + code.substring(LENGTH / 2);
    }
}
