package com.gabriellpa.sabadaco.music.lavaplayer;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param cipherUrl      servidor remoto de cipher do YouTube (ex.: yt-cipher); vazio desliga
 * @param cipherPassword senha do servidor de cipher, se houver
 */
@ConfigurationProperties(prefix = "sabadaco.youtube")
public record YoutubeProperties(String cipherUrl, String cipherPassword) {
}
