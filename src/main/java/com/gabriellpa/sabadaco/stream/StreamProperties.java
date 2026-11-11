package com.gabriellpa.sabadaco.stream;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Configuração do streaming de tela ({@code sabadaco.stream.*}).
 *
 * @param publicUrl endereço público do bot (o do Cloudflare Tunnel); os links das streams usam ele
 * @param codeTtl   validade dos códigos de entrada
 * @param node      número deste processo nos IDs snowflake (0 a 1023); só importa com mais de uma réplica
 */
@ConfigurationProperties(prefix = "sabadaco.stream")
public record StreamProperties(@DefaultValue("http://localhost:8080") String publicUrl,
                               @DefaultValue("5m") Duration codeTtl,
                               @DefaultValue("0") int node,
                               @DefaultValue LiveKit livekit) {

    /**
     * @param url       WebSocket do LiveKit (ex.: {@code wss://meu-projeto.livekit.cloud})
     * @param tokenTtl  janela para <b>conectar</b> com o token. Curta de propósito: se o token vazar, logo deixa de
     *                  servir. Quem já está na sala não cai quando ela passa, porque o LiveKit renova o token
     *                  sozinho para quem está conectado.
     */
    public record LiveKit(String url, String apiKey, String apiSecret, @DefaultValue("10m") Duration tokenTtl) {
    }
}
