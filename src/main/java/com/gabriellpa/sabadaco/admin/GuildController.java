package com.gabriellpa.sabadaco.admin;

import com.gabriellpa.sabadaco.discord.DiscordDirectory;
import com.gabriellpa.sabadaco.music.MusicService;
import com.gabriellpa.sabadaco.playlist.PlaylistService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.stream.Stream;

/** Um servidor: player, fila (remover, reordenar, limpar), volume e tocar música/playlist. */
@Controller
@RequestMapping("/admin/guilds/{guildId}")
@RequiredArgsConstructor
public class GuildController {

    /** Ações feitas pelo painel aparecem como "painel admin" no Discord. */
    private static final long ADMIN_REQUESTER = 0;

    private final AdminViews views;
    private final MusicService musicService;
    private final PlaylistService playlistService;
    private final DiscordDirectory directory;

    @GetMapping
    public String guild(@PathVariable long guildId, Model model) {
        model.addAttribute("card", views.guildCard(guildId));
        model.addAttribute("voiceChannels", directory.voiceChannels(guildId));
        model.addAttribute("playlists", Stream.concat(
                playlistService.search(null, guildId).stream(),
                playlistService.search(null, null).stream().filter(p -> p.guildId() == null)).toList());
        return "admin/guild";
    }

    @GetMapping("/state")
    public String state(@PathVariable long guildId, Model model) {
        model.addAttribute("card", views.guildCard(guildId));
        return "admin/fragments :: guildState";
    }

    @PostMapping("/control/{action}")
    public String control(@PathVariable long guildId, @PathVariable String action, Model model) {
        PlayerControls.apply(musicService, guildId, action);
        return state(guildId, model);
    }

    @PostMapping("/volume")
    public String volume(@PathVariable long guildId, @RequestParam int volume, Model model) {
        musicService.setVolume(guildId, volume);
        return state(guildId, model);
    }

    @PostMapping("/queue/{index}/remove")
    public String remove(@PathVariable long guildId, @PathVariable int index, Model model) {
        musicService.removeFromQueue(guildId, index);
        return state(guildId, model);
    }

    @PostMapping("/queue/{index}/move")
    public String move(@PathVariable long guildId, @PathVariable int index, @RequestParam int to, Model model) {
        musicService.moveInQueue(guildId, index, to);
        return state(guildId, model);
    }

    @PostMapping("/play")
    public String play(@PathVariable long guildId, @RequestParam long channelId, @RequestParam String query, Model model) {
        musicService.play(guildId, channelId, ADMIN_REQUESTER, query);
        return state(guildId, model);
    }

    @PostMapping("/play-playlist")
    public String playPlaylist(@PathVariable long guildId, @RequestParam long channelId, @RequestParam String playlistId,
                               @RequestParam(defaultValue = "false") boolean shuffle, Model model) {
        var playlist = playlistService.get(playlistId);
        var uris = playlist.tracks().stream().map(track -> track.uri()).toList();
        musicService.playPlaylist(guildId, channelId, playlist.ownerId(), playlist.name(), uris, shuffle);
        return state(guildId, model);
    }
}
