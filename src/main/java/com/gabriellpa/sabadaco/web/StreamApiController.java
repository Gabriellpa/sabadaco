package com.gabriellpa.sabadaco.web;

import com.gabriellpa.sabadaco.UserFacingException;
import com.gabriellpa.sabadaco.stream.DiscordLogin;
import com.gabriellpa.sabadaco.stream.StreamChatService;
import com.gabriellpa.sabadaco.stream.StreamService;
import com.gabriellpa.sabadaco.stream.StreamService.JoinTicket;
import com.gabriellpa.sabadaco.stream.StreamSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Adaptador web público da stream (sem login do painel: a proteção é o código de uso único).
 * Só traduz HTTP ↔ {@link StreamService}/{@link StreamChatService}; as regras ficam nos serviços e os erros em
 * {@link StreamApiErrorHandler}.
 *
 * <p>Depois de trocar o código, a página usa a sessão opaca no cabeçalho {@code Authorization: Bearer ...}.
 */
@RestController
@RequestMapping("/stream/api")
@RequiredArgsConstructor
public class StreamApiController {

    private static final String BEARER = "Bearer ";

    private final StreamService streams;
    private final StreamChatService chat;
    private final DiscordLogin discord;

    public record JoinRequest(String code) {
    }

    public record ChatRequest(String text) {
    }

    /** O que a Atividade manda: o código OAuth do SDK e onde ela foi aberta (IDs como texto, ver {@link StreamView}). */
    public record ActivityLoginRequest(String code, String guildId, String channelId) {
    }

    /** IDs vão como texto: snowflakes passam de 2^53 e o JavaScript arredondaria. */
    public record StreamView(String id, String title, String hostId, String hostName) {
        static StreamView of(StreamSession stream) {
            return new StreamView(String.valueOf(stream.id()), stream.title(),
                    String.valueOf(stream.hostUserId()), stream.hostName());
        }
    }

    /** @param session sessão da página (chat, lista de streams, reconectar sem código) */
    public record JoinResponse(String serverUrl, String token, String identity, boolean canPublish,
                               StreamView stream, List<StreamView> channelStreams, String session) {
        static JoinResponse of(JoinTicket ticket) {
            return new JoinResponse(ticket.serverUrl(), ticket.token(), ticket.identity(), ticket.canPublish(),
                    StreamView.of(ticket.stream()), views(ticket.channelStreams()), ticket.session());
        }
    }

    /** Troca o código pelo token do LiveKit e abre a sessão da página. */
    @PostMapping("/{streamId}/join")
    public JoinResponse join(@PathVariable long streamId, @RequestBody JoinRequest request) {
        return JoinResponse.of(streams.redeem(streamId, request.code()));
    }

    /**
     * Entrada pela Atividade do Discord: troca o código OAuth pela pessoa e, se ela estiver no canal de voz da
     * Atividade, abre a sessão como o código por DM faria.
     */
    @PostMapping("/activity/login")
    public JoinResponse activityLogin(@RequestBody ActivityLoginRequest request) {
        long guildId = id(request.guildId());
        long channelId = id(request.channelId());
        var user = discord.exchange(request.code());
        var ticket = streams.joinFromDiscord(guildId, channelId, user.id(), user.displayName());
        return JoinResponse.of(ticket);
    }

    /** Novo token do LiveKit pela sessão (F5, conexão caiu, passou a transmitir). */
    @PostMapping("/resume")
    public JoinResponse resume(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return JoinResponse.of(streams.resume(session(authorization)));
    }

    @GetMapping("/streams")
    public List<StreamView> channelStreams(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return views(streams.channelStreams(session(authorization)));
    }

    @PostMapping("/chat")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void chat(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
                     @RequestBody ChatRequest request) {
        chat.fromWeb(session(authorization), request.text());
    }

    private static List<StreamView> views(List<StreamSession> streams) {
        return streams.stream().map(StreamView::of).toList();
    }

    private static long id(String value) {
        try {
            return Long.parseLong(value == null ? "" : value.strip());
        } catch (NumberFormatException invalid) {
            throw new UserFacingException("Abra a Atividade dentro de um canal de voz.");
        }
    }

    /** Sem cabeçalho vira {@code null}, e o serviço responde "sessão terminou" (403). */
    private static String session(String authorization) {
        return authorization != null && authorization.startsWith(BEARER) ? authorization.substring(BEARER.length()).strip() : null;
    }
}
