package com.gabriellpa.sabadaco.stream;

/**
 * Porta para mandar no servidor de mídia depois que a pessoa já entrou: o token foi entregue, então quem tira
 * alguém da sala é o bot, pela API de servidor. Falhas não interrompem a regra (só são registradas).
 */
public interface MediaRooms {

    /** Desconecta a pessoa da sala (ex.: saiu do canal de voz). */
    void remove(String room, String identity);

    /** Tira a permissão de transmitir: a tela some, mas a pessoa continua assistindo às outras streams. */
    void revokePublish(String room, String identity);

    /** Entrega um dado (ex.: mensagem do chat em JSON) a todos na sala. */
    void broadcast(String room, byte[] data);
}
