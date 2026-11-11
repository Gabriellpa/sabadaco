package com.gabriellpa.sabadaco.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Dentro do Discord, a página da stream chega pelo proxy da Atividade com a raiz mapeada para
 * {@code stream.gabriellpa.com/stream/activity}. Assim, o que a página pede como {@code /js/stream.js} chega aqui
 * como {@code /stream/activity/js/stream.js}. Este filtro devolve esses pedidos ao caminho de sempre, para a mesma
 * página e a mesma API servirem o navegador e a Atividade.
 *
 * <p>Só repassa o que a página da stream usa: nada do painel ({@code /admin}) fica alcançável por aqui.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ActivityPathForwarding extends OncePerRequestFilter {

    static final String PREFIX = "/stream/activity";

    /** Caminhos que a página da stream usa (iguais aos liberados no Ingress). */
    private static final List<String> ALLOWED = List.of(
            "/stream/api/", "/css/stream.css", "/js/stream.js", "/js/vendor/discord-embedded-app-sdk.js",
            "/webjars/livekit-client/", "/stream/fonts/");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        var path = path(request);
        return !path.startsWith(PREFIX + "/") || path.equals(PREFIX + "/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var target = path(request).substring(PREFIX.length());
        if (target.contains("..") || ALLOWED.stream().noneMatch(target::startsWith)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        var query = request.getQueryString();
        request.getRequestDispatcher(query == null ? target : target + "?" + query).forward(request, response);
    }

    private static String path(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length());
    }
}
