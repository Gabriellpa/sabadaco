package com.gabriellpa.sabadaco.web;

import com.gabriellpa.sabadaco.stream.StreamService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * A página da stream (o link que vai por DM). Pública: sem o código ela só mostra o título e o campo do código.
 * Todo o resto (conectar, transmitir, chat) acontece no navegador, pela {@link StreamApiController} e pelo LiveKit.
 */
@Controller
@RequiredArgsConstructor
public class StreamPageController {

    private final StreamService streams;

    @GetMapping("/stream/{streamId:\\d+}")
    public String page(@PathVariable String streamId, Model model) {
        model.addAttribute("streamId", streamId);
        model.addAttribute("stream", parse(streamId) == null ? null : streams.get(parse(streamId)).orElse(null));
        return "stream/page";
    }

    private static Long parse(String id) {
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException tooLong) {
            return null;
        }
    }
}
