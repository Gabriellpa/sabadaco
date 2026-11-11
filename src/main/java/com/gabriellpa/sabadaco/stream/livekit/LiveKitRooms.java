package com.gabriellpa.sabadaco.stream.livekit;

import com.gabriellpa.sabadaco.stream.MediaRooms;
import com.gabriellpa.sabadaco.stream.StreamProperties;
import io.livekit.server.RoomServiceClient;
import livekit.LivekitModels.ParticipantPermission;
import lombok.extern.slf4j.Slf4j;
import retrofit2.Call;

import java.io.IOException;

/** {@link MediaRooms} pela API de servidor do LiveKit (HTTP, autenticada com a API key/secret). */
@Slf4j
public class LiveKitRooms implements MediaRooms {

    /** Pode assistir, não pode transmitir nem publicar dados (o chat passa pelo bot). */
    static final ParticipantPermission VIEW_ONLY = ParticipantPermission.newBuilder()
            .setCanSubscribe(true)
            .setCanPublish(false)
            .setCanPublishData(false)
            .build();

    private final RoomServiceClient client;

    public LiveKitRooms(StreamProperties.LiveKit properties) {
        this(configured(properties)
                ? RoomServiceClient.createClient(httpUrl(properties.url()), properties.apiKey(), properties.apiSecret())
                : null);
    }

    LiveKitRooms(RoomServiceClient client) {
        this.client = client;
    }

    @Override
    public void remove(String room, String identity) {
        if (client != null) {
            run("remover " + identity + " de " + room, client.removeParticipant(room, identity));
        }
    }

    @Override
    public void revokePublish(String room, String identity) {
        if (client != null) {
            run("tirar a transmissão de " + identity + " em " + room,
                    client.updateParticipant(room, identity, null, null, VIEW_ONLY));
        }
    }

    /** A API de servidor fala HTTP no mesmo host do WebSocket que o navegador usa. */
    static String httpUrl(String url) {
        return url.replaceFirst("^wss://", "https://").replaceFirst("^ws://", "http://");
    }

    private static boolean configured(StreamProperties.LiveKit properties) {
        return notBlank(properties.url()) && notBlank(properties.apiKey()) && notBlank(properties.apiSecret());
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static void run(String action, Call<?> call) {
        try {
            var response = call.execute();
            // 404: a pessoa (ou a sala) já não estava lá, que é o resultado que queríamos
            if (!response.isSuccessful() && response.code() != 404) {
                log.warn("LiveKit: falha ao {}: HTTP {}", action, response.code());
            }
        } catch (IOException | RuntimeException error) {
            log.warn("LiveKit: falha ao {}", action, error);
        }
    }
}
