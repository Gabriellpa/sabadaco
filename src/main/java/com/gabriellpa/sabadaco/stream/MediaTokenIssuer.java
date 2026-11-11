package com.gabriellpa.sabadaco.stream;

/**
 * Porta para o servidor de mídia (SFU). Hoje é o LiveKit ({@code LiveKitTokenIssuer}); o núcleo só pede
 * um token de acesso à sala e a URL onde o navegador conecta.
 */
public interface MediaTokenIssuer {

    /**
     * @param identity  identificador estável da pessoa na sala (o id do Discord)
     * @param canPublish se pode transmitir a tela (só quem abriu uma stream no canal)
     * @param metadata   texto visível para todos na sala; usamos o id da stream de quem transmite
     */
    String token(String room, String identity, String displayName, boolean canPublish, String metadata);

    /** URL WebSocket do servidor de mídia, usada pelo navegador. */
    String serverUrl();

    /** Falso quando faltam as credenciais: os comandos avisam em vez de gerar links que não funcionam. */
    boolean isConfigured();
}
