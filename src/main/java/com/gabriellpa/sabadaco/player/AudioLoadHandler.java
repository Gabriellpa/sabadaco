package com.gabriellpa.sabadaco.player;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

@RequiredArgsConstructor
public class AudioLoadHandler implements AudioLoadResultHandler {

    private final GuildMusicManager musicManager;
    //todo: remover, essa classe deve só ser responsável por carregar as tracks da queue
    private final TextChannel channel;

    @Override
    public void trackLoaded(AudioTrack track) {
        musicManager.addTrack(track);
        channel.sendMessage("Adicionada à fila: " + track.getInfo().title).queue();
    }

    @Override
    public void playlistLoaded(AudioPlaylist playlist) {
        AudioTrack firstTrack = playlist.getSelectedTrack();
        if (firstTrack == null) {
            firstTrack = playlist.getTracks().getFirst();
        }
        musicManager.addTrack(firstTrack);
        channel.sendMessage("Adicionada à fila: " + firstTrack.getInfo().title).queue();
    }

    @Override
    public void noMatches() {
        channel.sendMessage("Nenhuma música encontrada.").queue();
    }

    @Override
    public void loadFailed(FriendlyException exception) {
        channel.sendMessage("Erro ao carregar música: " + exception.getMessage()).queue();
    }

}