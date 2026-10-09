package com.gabriellpa.sabadaco.playlist;

import com.gabriellpa.sabadaco.DiscordAdmins;
import com.gabriellpa.sabadaco.UserFacingException;
import com.gabriellpa.sabadaco.music.TrackSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Regras das playlists. Toda operação de escrita exige o dono ({@code ownerId}); o painel admin
 * age em nome do dono da playlist. Admins do Discord ({@link DiscordAdmins}) veem e tocam qualquer playlist.
 */
@Service
@RequiredArgsConstructor
public class PlaylistService {

    static final int MAX_NAME_LENGTH = 50;
    static final int MAX_ALIAS_LENGTH = 40;
    static final int MAX_TRACKS = 500;

    private static final Comparator<Playlist> ORDER = Comparator.comparing(Playlist::scope).thenComparing(Playlist::name, String.CASE_INSENSITIVE_ORDER);

    private final PlaylistRepository repository;
    private final DiscordAdmins admins;

    /** @param scope {@code null} = {@link PlaylistScope#GUILD} */
    public synchronized Playlist create(long ownerId, long guildId, String name, PlaylistScope scope) {
        var effectiveScope = scope == null ? PlaylistScope.GUILD : scope;
        var guild = effectiveScope == PlaylistScope.GUILD ? guildId : null;
        var cleanName = validName(name);
        ensureUniqueName(ownerId, effectiveScope, guild, cleanName, null);
        return repository.save(new Playlist(null, ownerId, effectiveScope, guild, cleanName, List.of()));
    }

    /** Playlists que o usuário pode usar neste servidor: as do servidor + as globais dele. */
    public List<Playlist> visibleTo(long ownerId, long guildId) {
        return repository.findByOwner(ownerId).stream()
                .filter(playlist -> playlist.visibleIn(guildId))
                .sorted(ORDER)
                .toList();
    }

    /**
     * Playlists que o usuário pode escolher nos comandos: as visíveis para ele e, se for admin,
     * as de todo mundo em seguida (as dele vêm primeiro, para nomes iguais resolverem para a dele).
     */
    public List<Playlist> browsableBy(long userId, long guildId) {
        var own = visibleTo(userId, guildId);
        if (!admins.isAdmin(userId)) {
            return own;
        }
        var others = repository.findAll().stream()
                .filter(playlist -> playlist.ownerId() != userId)
                .sorted(Comparator.comparingLong(Playlist::ownerId).thenComparing(ORDER))
                .toList();
        var result = new ArrayList<>(own);
        result.addAll(others);
        return result;
    }

    /** Playlist que o usuário pode ver e tocar: a dele ou, para admins, qualquer uma. */
    public Playlist playable(long userId, String id) {
        var playlist = get(id);
        if (playlist.ownerId() != userId && !admins.isAdmin(userId)) {
            throw new UserFacingException("Essa playlist não é sua.");
        }
        return playlist;
    }

    public boolean isAdmin(long userId) {
        return admins.isAdmin(userId);
    }

    /** Filtro do painel admin; parâmetros nulos não filtram. */
    public List<Playlist> search(Long ownerId, Long guildId) {
        return repository.findAll().stream()
                .filter(playlist -> ownerId == null || playlist.ownerId() == ownerId)
                .filter(playlist -> guildId == null || Objects.equals(playlist.guildId(), guildId))
                .sorted(Comparator.comparingLong(Playlist::ownerId).thenComparing(ORDER))
                .toList();
    }

    public Playlist get(String id) {
        return repository.findById(id).orElseThrow(() -> new UserFacingException("Playlist não encontrada."));
    }

    public Playlist owned(long ownerId, String id) {
        var playlist = get(id);
        if (playlist.ownerId() != ownerId) {
            throw new UserFacingException("Essa playlist não é sua.");
        }
        return playlist;
    }

    public synchronized Playlist rename(long ownerId, String id, String newName) {
        var playlist = owned(ownerId, id);
        var cleanName = validName(newName);
        ensureUniqueName(ownerId, playlist.scope(), playlist.guildId(), cleanName, id);
        return repository.save(playlist.withName(cleanName));
    }

    public synchronized void delete(long ownerId, String id) {
        owned(ownerId, id);
        repository.delete(id);
    }

    public synchronized PlaylistTrack addTrack(long ownerId, String id, TrackSummary track, String alias) {
        var playlist = owned(ownerId, id);
        if (playlist.tracks().size() >= MAX_TRACKS) {
            throw new UserFacingException("A playlist chegou ao limite de " + MAX_TRACKS + " músicas.");
        }
        var added = PlaylistTrack.of(track, validAlias(alias));
        repository.save(playlist.withTracks(append(playlist.tracks(), added)));
        return added;
    }

    public synchronized PlaylistTrack setAlias(long ownerId, String id, int index, String alias) {
        var playlist = owned(ownerId, id);
        var tracks = new ArrayList<>(playlist.tracks());
        var updated = trackAt(playlist, index).withAlias(validAlias(alias));
        tracks.set(index, updated);
        repository.save(playlist.withTracks(tracks));
        return updated;
    }

    public synchronized PlaylistTrack removeTrack(long ownerId, String id, int index) {
        var playlist = owned(ownerId, id);
        var tracks = new ArrayList<>(playlist.tracks());
        var removed = trackAt(playlist, index);
        tracks.remove(index);
        repository.save(playlist.withTracks(tracks));
        return removed;
    }

    /** "Mova a música A da playlist 1 para a playlist 2" (o apelido vai junto). */
    public synchronized PlaylistTrack moveTrack(long ownerId, String fromId, int index, String toId) {
        if (fromId.equals(toId)) {
            throw new UserFacingException("Escolha playlists diferentes.");
        }
        var target = owned(ownerId, toId);
        if (target.tracks().size() >= MAX_TRACKS) {
            throw new UserFacingException("A playlist " + target.name() + " está cheia.");
        }
        var moved = removeTrack(ownerId, fromId, index);
        repository.save(target.withTracks(append(target.tracks(), moved)));
        return moved;
    }

    /** Músicas com apelido visíveis para o usuário, para atalhos no {@code /play}. */
    public List<PlaylistTrack> aliasedTracks(long ownerId, long guildId) {
        return visibleTo(ownerId, guildId).stream()
                .flatMap(playlist -> playlist.tracks().stream())
                .filter(track -> track.alias() != null)
                .toList();
    }

    public long count() {
        return repository.findAll().size();
    }

    private void ensureUniqueName(long ownerId, PlaylistScope scope, Long guildId, String name, String ignoreId) {
        boolean exists = repository.findByOwner(ownerId).stream()
                .filter(playlist -> !playlist.id().equals(ignoreId))
                .anyMatch(playlist -> playlist.scope() == scope
                        && Objects.equals(playlist.guildId(), guildId)
                        && playlist.name().equalsIgnoreCase(name));
        if (exists) {
            throw new UserFacingException("Você já tem uma playlist chamada " + name + ".");
        }
    }

    private static PlaylistTrack trackAt(Playlist playlist, int index) {
        if (index < 0 || index >= playlist.tracks().size()) {
            throw new UserFacingException("Música #" + (index + 1) + " não existe na playlist " + playlist.name() + ".");
        }
        return playlist.tracks().get(index);
    }

    private static List<PlaylistTrack> append(List<PlaylistTrack> tracks, PlaylistTrack track) {
        var result = new ArrayList<>(tracks);
        result.add(track);
        return result;
    }

    private static String validName(String name) {
        if (name == null || name.isBlank()) {
            throw new UserFacingException("Informe um nome para a playlist.");
        }
        var clean = name.trim();
        if (clean.length() > MAX_NAME_LENGTH) {
            throw new UserFacingException("Nome muito longo (máximo " + MAX_NAME_LENGTH + " caracteres).");
        }
        return clean;
    }

    private static String validAlias(String alias) {
        if (alias != null && alias.trim().length() > MAX_ALIAS_LENGTH) {
            throw new UserFacingException("Apelido muito longo (máximo " + MAX_ALIAS_LENGTH + " caracteres).");
        }
        return alias;
    }
}
