package com.gabriellpa.sabadaco.stream;

/**
 * Porta para o lado do Discord quando uma stream abre fora de um comando (pela Atividade): publica o card no chat e
 * marca quem transmite, como o {@code /stream create} faz.
 */
public interface StreamNotifier {

    void started(StreamSession stream);
}
