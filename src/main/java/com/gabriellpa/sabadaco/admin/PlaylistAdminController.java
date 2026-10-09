package com.gabriellpa.sabadaco.admin;

import com.gabriellpa.sabadaco.UserFacingException;
import com.gabriellpa.sabadaco.discord.DiscordDirectory;
import com.gabriellpa.sabadaco.music.MusicService;
import com.gabriellpa.sabadaco.playlist.Playlist;
import com.gabriellpa.sabadaco.playlist.PlaylistScope;
import com.gabriellpa.sabadaco.playlist.PlaylistService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Gestão das playlists de cada pessoa. O admin age em nome do dono da playlist.
 */
@Controller
@RequestMapping("/admin/playlists")
@RequiredArgsConstructor
public class PlaylistAdminController {

    private final PlaylistService playlistService;
    private final MusicService musicService;
    private final DiscordDirectory directory;

    @GetMapping
    public String list(@RequestParam(required = false) Long owner, @RequestParam(required = false) Long guild, Model model) {
        model.addAttribute("playlists", playlistService.search(owner, guild));
        model.addAttribute("owner", owner);
        model.addAttribute("guild", guild);
        model.addAttribute("users", directory.knownUsers());
        model.addAttribute("guilds", directory.guilds());
        return "admin/playlists";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable String id, Model model) {
        var playlist = playlistService.get(id);
        model.addAttribute("playlist", playlist);
        model.addAttribute("siblings", playlistService.search(playlist.ownerId(), null).stream()
                .filter(other -> !other.id().equals(id))
                .toList());
        return "admin/playlist";
    }

    @PostMapping
    public String create(@RequestParam(required = false) Long ownerId, @RequestParam(required = false) Long guildId,
                         @RequestParam String name, @RequestParam PlaylistScope scope) {
        if (scope != PlaylistScope.GLOBAL && guildId == null) {
            throw new UserFacingException("Escolha o servidor da playlist.");
        }
        if (scope == PlaylistScope.SERVER) {
            return redirect(playlistService.createForServer(guildId, name));
        }
        if (ownerId == null) {
            throw new UserFacingException("Informe o dono (id do usuário).");
        }
        var created = playlistService.create(ownerId, guildId == null ? 0 : guildId, name, scope);
        return redirect(created);
    }

    @PostMapping("/{id}/rename")
    public String rename(@PathVariable String id, @RequestParam String name) {
        return redirect(playlistService.rename(owner(id), id, name));
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable String id) {
        playlistService.delete(owner(id), id);
        return "redirect:/admin/playlists";
    }

    @PostMapping("/{id}/tracks")
    public String addTrack(@PathVariable String id, @RequestParam String query, @RequestParam(required = false) String alias) {
        playlistService.addTrack(owner(id), id, musicService.resolve(query), alias);
        return "redirect:/admin/playlists/" + id;
    }

    @PostMapping("/{id}/tracks/{index}/alias")
    public String alias(@PathVariable String id, @PathVariable int index, @RequestParam(required = false) String alias) {
        playlistService.setAlias(owner(id), id, index, alias);
        return "redirect:/admin/playlists/" + id;
    }

    @PostMapping("/{id}/tracks/{index}/remove")
    public String removeTrack(@PathVariable String id, @PathVariable int index) {
        playlistService.removeTrack(owner(id), id, index);
        return "redirect:/admin/playlists/" + id;
    }

    @PostMapping("/{id}/tracks/{index}/move")
    public String moveTrack(@PathVariable String id, @PathVariable int index, @RequestParam String to) {
        playlistService.moveTrack(owner(id), id, index, to);
        return "redirect:/admin/playlists/" + id;
    }

    private long owner(String id) {
        return playlistService.get(id).ownerId();
    }

    private static String redirect(Playlist playlist) {
        return "redirect:/admin/playlists/" + playlist.id();
    }
}
