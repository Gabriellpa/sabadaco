package com.gabriellpa.sabadaco.admin.listen;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Estrutura das páginas Ogg. (A compatibilidade real foi validada decodificando com ffmpeg 5 s de áudio
 * do Lavaplayer: 250 frames → 00:00:05.00, opus 48 kHz estéreo, sem erros.)
 */
class OggOpusWriterTest {

    @Test
    void writesHeadersThenOnePagePerPacketWithValidCrcAndGranule() throws Exception {
        var out = new ByteArrayOutputStream();
        var writer = new OggOpusWriter(out);
        writer.writeHeaders();
        writer.writePacket(new byte[300]);
        writer.writePacket(new byte[10]);

        var pages = pages(out.toByteArray());

        assertThat(pages).hasSize(4);
        assertThat(new String(pages.get(0), 28, 8)).isEqualTo("OpusHead");
        assertThat(pages.get(0)[5]).isEqualTo((byte) 0x02); // início do stream
        assertThat(new String(pages.get(1), 28, 8)).isEqualTo("OpusTags");
        // pacote de 300 bytes = segmentos 255 + 45
        assertThat(pages.get(2)[26]).isEqualTo((byte) 2);
        assertThat(pages.get(2)[27] & 0xff).isEqualTo(255);
        assertThat(pages.get(2)[28] & 0xff).isEqualTo(45);
        assertThat(granule(pages.get(2))).isEqualTo(960);
        assertThat(granule(pages.get(3))).isEqualTo(1920);
        pages.forEach(page -> {
            var copy = page.clone();
            int stored = ByteBuffer.wrap(copy, 22, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
            Arrays.fill(copy, 22, 26, (byte) 0);
            assertThat(OggOpusWriter.crc(copy)).isEqualTo(stored);
        });
    }

    private static long granule(byte[] page) {
        return ByteBuffer.wrap(page, 6, 8).order(ByteOrder.LITTLE_ENDIAN).getLong();
    }

    private static List<byte[]> pages(byte[] stream) {
        var pages = new ArrayList<byte[]>();
        int offset = 0;
        while (offset < stream.length) {
            int segments = stream[offset + 26] & 0xff;
            int body = 0;
            for (int i = 0; i < segments; i++) {
                body += stream[offset + 27 + i] & 0xff;
            }
            int length = 27 + segments + body;
            pages.add(Arrays.copyOfRange(stream, offset, offset + length));
            offset += length;
        }
        return pages;
    }
}
