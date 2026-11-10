package com.gabriellpa.sabadaco.stream;

import java.time.Clock;
import java.time.Instant;

/**
 * IDs no formato snowflake (como os do Discord): 41 bits de milissegundos desde {@link #EPOCH},
 * 10 bits de nó e 12 bits de sequência. Ordenáveis pelo tempo e únicos sem banco.
 */
public class SnowflakeGenerator {

    /** 2026-01-01T00:00:00Z. */
    public static final long EPOCH = Instant.parse("2026-01-01T00:00:00Z").toEpochMilli();

    private static final int NODE_BITS = 10;
    private static final int SEQUENCE_BITS = 12;
    private static final long MAX_SEQUENCE = (1L << SEQUENCE_BITS) - 1;

    private final Clock clock;
    private final long node;
    private long lastMillis = -1;
    private long sequence;

    public SnowflakeGenerator(Clock clock, int node) {
        if (node < 0 || node >= (1 << NODE_BITS)) {
            throw new IllegalArgumentException("node deve estar entre 0 e " + ((1 << NODE_BITS) - 1));
        }
        this.clock = clock;
        this.node = node;
    }

    public synchronized long next() {
        long now = Math.max(clock.millis(), lastMillis); // relógio voltando não gera ID repetido
        if (now == lastMillis) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0) {
                now = lastMillis + 1; // esgotou a sequência neste ms: usa o próximo
            }
        } else {
            sequence = 0;
        }
        lastMillis = now;
        return ((now - EPOCH) << (NODE_BITS + SEQUENCE_BITS)) | (node << SEQUENCE_BITS) | sequence;
    }

    public static Instant timestampOf(long id) {
        return Instant.ofEpochMilli((id >>> (NODE_BITS + SEQUENCE_BITS)) + EPOCH);
    }
}
