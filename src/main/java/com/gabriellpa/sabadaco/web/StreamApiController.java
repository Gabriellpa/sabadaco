package com.gabriellpa.sabadaco.web;

import com.gabriellpa.sabadaco.stream.StreamService;
import com.gabriellpa.sabadaco.stream.StreamSession;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Adaptador web público da stream (sem login do painel: a proteção é o código de uso único).
 * Só traduz HTTP ↔ {@link StreamService}; as regras ficam no serviço e os erros em {@link StreamApiErrorHandler}.
 */
@RestController
@RequestMapping("/stream/api")
@RequiredArgsConstructor
public class StreamApiController {

    private final StreamService streams;

    public record JoinRequest(String code) {
    }

    /** IDs vão como texto: snowflakes passam de 2^53 e o JavaScript arredondaria. */
    public record StreamView(String id, String title, String hostId, String hostName) {
        static StreamView of(StreamSession stream) {
            return new StreamView(String.valueOf(stream.id()), stream.title(),
                    String.valueOf(stream.hostUserId()), stream.hostName());
        }
    }

    public record JoinResponse(String serverUrl, String token, String identity, boolean canPublish,
                               StreamView stream, List<StreamView> channelStreams) {
    }

    /** Troca o código pelo token do LiveKit. */
    @PostMapping("/{streamId}/join")
    public JoinResponse join(@PathVariable long streamId, @RequestBody JoinRequest request) {
        var ticket = streams.redeem(streamId, request.code());
        return new JoinResponse(ticket.serverUrl(), ticket.token(), ticket.identity(), ticket.canPublish(),
                StreamView.of(ticket.stream()), ticket.channelStreams().stream().map(StreamView::of).toList());
    }

}
