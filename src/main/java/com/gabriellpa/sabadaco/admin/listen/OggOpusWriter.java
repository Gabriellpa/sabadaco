package com.gabriellpa.sabadaco.admin.listen;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Empacota pacotes Opus (os mesmos frames de 20 ms enviados ao Discord) num stream Ogg
 * (RFC 3533 + RFC 7845), formato que o {@code <audio>} do navegador toca direto.
 * <p>
 * Cada pacote vai numa página própria: simples e com latência mínima; o overhead (~30 bytes por
 * página) é irrelevante perto do áudio.
 */
public class OggOpusWriter {

    /** O Lavaplayer entrega Opus 48 kHz estéreo em frames de 20 ms = 960 amostras. */
    private static final int SAMPLES_PER_FRAME = 960;
    private static final int[] CRC_TABLE = crcTable();

    private final OutputStream out;
    private final int serial = ThreadLocalRandom.current().nextInt();
    private int sequence;
    private long granule;

    public OggOpusWriter(OutputStream out) {
        this.out = out;
    }

    /** Cabeçalhos obrigatórios: OpusHead (página inicial) e OpusTags. */
    public void writeHeaders() throws IOException {
        var head = ByteBuffer.allocate(19).order(ByteOrder.LITTLE_ENDIAN)
                .put("OpusHead".getBytes(StandardCharsets.US_ASCII))
                .put((byte) 1)          // versão
                .put((byte) 2)          // canais
                .putShort((short) 0)    // pre-skip
                .putInt(48_000)         // taxa original
                .putShort((short) 0)    // ganho
                .put((byte) 0);         // mapeamento: mono/estéreo
        writePage(head.array(), 0x02, 0);

        var vendor = "sabadaco".getBytes(StandardCharsets.UTF_8);
        var tags = ByteBuffer.allocate(8 + 4 + vendor.length + 4).order(ByteOrder.LITTLE_ENDIAN)
                .put("OpusTags".getBytes(StandardCharsets.US_ASCII))
                .putInt(vendor.length)
                .put(vendor)
                .putInt(0);             // nenhum comentário
        writePage(tags.array(), 0x00, 0);
    }

    public void writePacket(byte[] opusPacket) throws IOException {
        granule += SAMPLES_PER_FRAME;
        writePage(opusPacket, 0x00, granule);
    }

    private void writePage(byte[] packet, int headerType, long granulePosition) throws IOException {
        // Tabela de "lacing": o pacote é dividido em segmentos de até 255 bytes; o último é < 255
        int segments = packet.length / 255 + 1;
        var page = ByteBuffer.allocate(27 + segments + packet.length).order(ByteOrder.LITTLE_ENDIAN)
                .put("OggS".getBytes(StandardCharsets.US_ASCII))
                .put((byte) 0)
                .put((byte) headerType)
                .putLong(granulePosition)
                .putInt(serial)
                .putInt(sequence++)
                .putInt(0)              // CRC, preenchido abaixo
                .put((byte) segments);
        for (int i = 0; i < segments - 1; i++) {
            page.put((byte) 255);
        }
        page.put((byte) (packet.length % 255));
        page.put(packet);

        var bytes = page.array();
        int crc = crc(bytes);
        ByteBuffer.wrap(bytes, 22, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(crc);
        out.write(bytes);
    }

    /** CRC-32 do Ogg: polinômio 0x04C11DB7, sem reflexão, valor inicial 0. */
    static int crc(byte[] data) {
        int crc = 0;
        for (byte b : data) {
            crc = (crc << 8) ^ CRC_TABLE[((crc >>> 24) ^ (b & 0xff)) & 0xff];
        }
        return crc;
    }

    private static int[] crcTable() {
        var table = new int[256];
        for (int i = 0; i < 256; i++) {
            int r = i << 24;
            for (int bit = 0; bit < 8; bit++) {
                r = (r & 0x80000000) != 0 ? (r << 1) ^ 0x04C11DB7 : r << 1;
            }
            table[i] = r;
        }
        return table;
    }
}
