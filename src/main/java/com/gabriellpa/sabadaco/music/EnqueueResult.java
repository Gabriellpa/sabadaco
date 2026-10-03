package com.gabriellpa.sabadaco.music;

import java.util.List;

/**
 * Resultado de enfileirar faixas, com o necessário para avisar o usuário sobre a regra de prioridade:
 * músicas avulsas tocam antes do restante de uma playlist.
 *
 * @param position             posição da primeira faixa adicionada (0 = começou a tocar agora)
 * @param skippedPlaylistTracks faixas de playlist que a música avulsa "furou" (0 se não houve)
 * @param skippedPlaylistName  nome da playlist que foi "furada", se houver
 * @param singlesAhead         músicas avulsas que vão tocar antes da playlist adicionada
 * @param failed               faixas que não puderam ser carregadas
 */
public record EnqueueResult(
        List<TrackSummary> added,
        String playlistName,
        int position,
        int skippedPlaylistTracks,
        String skippedPlaylistName,
        int singlesAhead,
        int failed
) {

    public boolean startedNow() {
        return position == 0;
    }

    public TrackSummary first() {
        return added.getFirst();
    }

    EnqueueResult withFailed(int failed) {
        return new EnqueueResult(added, playlistName, position, skippedPlaylistTracks, skippedPlaylistName, singlesAhead, failed);
    }
}
