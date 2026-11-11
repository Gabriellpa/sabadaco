package com.gabriellpa.sabadaco.stream;

/**
 * Login com a conta do Discord, usado pela Atividade (a stream aberta dentro do Discord). Lá não tem código por DM:
 * o SDK do Discord entrega um código OAuth e o bot troca por quem a pessoa é de verdade.
 */
public interface DiscordLogin {

    /** Quem abriu a Atividade. O token OAuth da pessoa só serve para descobrir isso e é descartado. */
    record DiscordUser(long id, String displayName) {
    }

    /** Sem o client secret do app, a Atividade fica desativada. */
    boolean isConfigured();

    /** ID do app no Discord, que a página passa para o SDK. */
    String clientId();

    /**
     * Troca o código OAuth (escopo {@code identify}) pela pessoa dona dele.
     *
     * @throws com.gabriellpa.sabadaco.UserFacingException se o código for inválido ou o Discord recusar
     */
    DiscordUser exchange(String code);
}
