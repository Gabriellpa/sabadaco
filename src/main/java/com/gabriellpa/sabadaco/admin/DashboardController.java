package com.gabriellpa.sabadaco.admin;

import com.gabriellpa.sabadaco.music.MusicService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

/** Visão geral: o que toca em cada servidor, com controles rápidos (atualiza a cada 2s via HTMX). */
@Controller
@RequiredArgsConstructor
public class DashboardController {

    private final AdminViews views;
    private final MusicService musicService;

    @GetMapping("/")
    public String root() {
        return "redirect:/admin";
    }

    @GetMapping("/admin")
    public String dashboard(Model model) {
        model.addAttribute("stats", views.stats());
        model.addAttribute("cards", views.guildCards());
        return "admin/dashboard";
    }

    @GetMapping("/admin/players")
    public String players(Model model) {
        model.addAttribute("cards", views.guildCards());
        return "admin/fragments :: players";
    }

    @PostMapping("/admin/players/{guildId}/{action}")
    public String control(@PathVariable long guildId, @PathVariable String action, Model model) {
        PlayerControls.apply(musicService, guildId, action);
        return players(model);
    }
}
