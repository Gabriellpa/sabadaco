package com.gabriellpa.sabadaco.metrics;

import io.micrometer.core.instrument.Counter;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.entity.HttpEntityWrapper;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Conta os bytes efetivamente lidos de uma resposta HTTP (download de áudio do Lavaplayer).
 */
public class CountingHttpEntity extends HttpEntityWrapper {

    private final Counter counter;

    public CountingHttpEntity(HttpEntity wrapped, Counter counter) {
        super(wrapped);
        this.counter = counter;
    }

    /** Interceptor para registrar no {@code HttpClientBuilder} dos sources do Lavaplayer. */
    public static HttpResponseInterceptor interceptor(Counter counter) {
        return (response, context) -> {
            if (response.getEntity() != null) {
                response.setEntity(new CountingHttpEntity(response.getEntity(), counter));
            }
        };
    }

    @Override
    public InputStream getContent() throws IOException {
        return new FilterInputStream(super.getContent()) {
            @Override
            public int read() throws IOException {
                int value = super.read();
                if (value >= 0) {
                    counter.increment();
                }
                return value;
            }

            @Override
            public int read(byte[] buffer, int offset, int length) throws IOException {
                int read = super.read(buffer, offset, length);
                if (read > 0) {
                    counter.increment(read);
                }
                return read;
            }
        };
    }
}
