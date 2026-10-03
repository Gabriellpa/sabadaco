package com.gabriellpa.sabadaco.music.lavaplayer;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param cipherUrl          servidor remoto de cipher do YouTube (ex.: yt-cipher); vazio desliga
 * @param cipherPassword     senha do servidor de cipher, se houver
 * @param oauthEnabled       liga o login OAuth (client TV). Necessário quando o YouTube passa a exigir
 *                           login para tocar ("This video requires login"). Use uma conta descartável.
 * @param oauthRefreshToken  refresh token já obtido; vazio = inicia o fluxo de dispositivo e mostra o
 *                           código no log (https://www.google.com/device)
 */
@ConfigurationProperties(prefix = "sabadaco.youtube")
public record YoutubeProperties(String cipherUrl, String cipherPassword, boolean oauthEnabled, String oauthRefreshToken) {
}
