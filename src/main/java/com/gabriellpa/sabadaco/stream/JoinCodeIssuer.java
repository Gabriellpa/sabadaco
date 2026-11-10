package com.gabriellpa.sabadaco.stream;

import java.time.Instant;
import java.util.Optional;

/**
 * Porta para os códigos de entrada. Hoje a implementação mora no bot ({@link InMemoryJoinCodeIssuer});
 * se virar um serviço separado, basta outra implementação que chame a API, e os comandos não mudam.
 *
 * <p>Regras: o código é <b>da pessoa</b>, vale para <b>todas as streams do canal de voz</b> onde ela
 * estava ao pedir, expira (5 min por padrão) e é de <b>uso único</b>. Pedir outro invalida o anterior.
 */
public interface JoinCodeIssuer {

    JoinCode issue(long guildId, long voiceChannelId, long userId, String displayName);

    /** Consome o código: vale uma vez só, mesmo que a validação seguinte falhe. */
    Optional<JoinGrant> redeem(String code);

    /** Invalida os códigos pendentes da pessoa no servidor (ex.: saiu do canal de voz). */
    void revoke(long guildId, long userId);

    record JoinCode(String code, Instant expiresAt) {
    }

    /** Para quem e para qual canal de voz o código foi emitido. */
    record JoinGrant(long guildId, long voiceChannelId, long userId, String displayName) {
    }
}
