package com.gabriellpa.sabadaco.admin;

import com.gabriellpa.sabadaco.UserFacingException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.net.URI;

/**
 * Erros de regra viram aviso na tela: um toast nas chamadas HTMX ou mensagem flash após redirect.
 */
@ControllerAdvice(basePackageClasses = AdminErrorHandler.class)
public class AdminErrorHandler {

    @ExceptionHandler(UserFacingException.class)
    public String handle(UserFacingException exception, HttpServletRequest request, HttpServletResponse response,
                         Model model, RedirectAttributes redirect) {
        if (request.getHeader("HX-Request") != null) {
            response.setHeader("HX-Retarget", "#toast");
            response.setHeader("HX-Reswap", "innerHTML");
            model.addAttribute("message", exception.getMessage());
            return "admin/fragments :: toast";
        }
        redirect.addFlashAttribute("error", exception.getMessage());
        return "redirect:" + backPath(request.getHeader("Referer"));
    }

    /** Volta para a página de origem, aceitando só caminhos do próprio admin. */
    private static String backPath(String referer) {
        try {
            var uri = URI.create(referer);
            if (uri.getPath() != null && uri.getPath().startsWith("/admin")) {
                return uri.getPath() + (uri.getQuery() == null ? "" : "?" + uri.getQuery());
            }
        } catch (IllegalArgumentException | NullPointerException ignored) {
            // sem referer válido: volta para o início
        }
        return "/admin";
    }
}
