package com.gabriellpa.sabadaco.stream.web;

import com.gabriellpa.sabadaco.UserFacingException;
import com.gabriellpa.sabadaco.stream.StreamService;
import com.gabriellpa.sabadaco.stream.StreamSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * API pública da página da stream (sem login do painel: a proteção é o código de uso único).
 * {@code POST /stream/api/{id}/join} troca o código pelo token do LiveKit.
 */
@RestController
@RequestMapping("/stream/api")
public class StreamApiController {

    private final StreamService streams;

    public StreamApiController(StreamService streams) {
        this.streams = streams;
    }

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

    @PostMapping("/{streamId}/join")
    public JoinResponse join(@PathVariable long streamId, @RequestBody JoinRequest request) {
        var ticket = streams.redeem(streamId, request.code());
        return new JoinResponse(ticket.serverUrl(), ticket.token(), ticket.identity(), ticket.canPublish(),
                StreamView.of(ticket.stream()), ticket.channelStreams().stream().map(StreamView::of).toList());
    }

    @ExceptionHandler(UserFacingException.class)
    public ResponseEntity<Map<String, String>> userError(UserFacingException error) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", error.getMessage()));
    }
}
