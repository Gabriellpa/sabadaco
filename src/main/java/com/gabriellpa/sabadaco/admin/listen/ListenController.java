package com.gabriellpa.sabadaco.admin.listen;

import com.gabriellpa.sabadaco.music.GuildPlayer;
import com.gabriellpa.sabadaco.music.GuildPlayerRegistry;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * "Ouvir pelo painel": transmite ao navegador exatamente o áudio que o bot está enviando ao Discord.
 * <p>
 * Escrita síncrona na resposta: roda numa virtual thread do Tomcat ({@code spring.threads.virtual.enabled}),
 * então bloquear esperando frames não custa uma thread de verdade e não esbarra no timeout de requisições
 * assíncronas do Spring MVC.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class ListenController {

    /** ~5 s de áudio: se o navegador ficar para trás, frames velhos são descartados em vez de travar o bot. */
    private static final int BUFFER_FRAMES = 250;
    /** Sem áudio (pausado/parado) por este tempo, encerra o stream. */
    private static final long IDLE_TIMEOUT_SECONDS = 120;

    private final GuildPlayerRegistry players;

    @GetMapping("/admin/guilds/{guildId}/listen")
    public void listen(@PathVariable long guildId, HttpServletResponse response) throws IOException {
        GuildPlayer player = players.find(guildId).orElse(null);
        if (player == null) {
            // Requisição de <audio>: um 404 simples, não a página de erro/redirect do painel
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Nada tocando neste servidor.");
            return;
        }
        response.setContentType("audio/ogg");
        response.setHeader("Cache-Control", "no-store");

        var frames = new ArrayBlockingQueue<byte[]>(BUFFER_FRAMES);
        var out = response.getOutputStream();
        var ogg = new OggOpusWriter(out);
        try (var ignored = player.tap(frames::offer)) {
            ogg.writeHeaders();
            out.flush();
            long idleSince = System.nanoTime();
            while (true) {
                var frame = frames.poll(1, TimeUnit.SECONDS);
                if (frame == null) {
                    if (System.nanoTime() - idleSince > TimeUnit.SECONDS.toNanos(IDLE_TIMEOUT_SECONDS)) {
                        return;
                    }
                    continue;
                }
                idleSince = System.nanoTime();
                ogg.writePacket(frame);
                if (frames.isEmpty()) {
                    out.flush();
                }
            }
        } catch (IOException clientWentAway) {
            log.debug("Ouvinte do painel desconectou da guild {}", guildId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.warn("Falha no stream de áudio da guild {}: {}", guildId, e.getMessage());
        }
    }
}
