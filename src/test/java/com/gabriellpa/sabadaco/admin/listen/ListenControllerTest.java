package com.gabriellpa.sabadaco.admin.listen;

import org.junit.jupiter.api.Test;

import java.util.concurrent.ArrayBlockingQueue;

import static org.assertj.core.api.Assertions.assertThat;

class ListenControllerTest {

    @Test
    void fullBufferDropsOldestFrameToStayLive() {
        var frames = new ArrayBlockingQueue<byte[]>(2);

        ListenController.keepLatest(frames, new byte[]{1});
        ListenController.keepLatest(frames, new byte[]{2});
        ListenController.keepLatest(frames, new byte[]{3});

        assertThat(frames).extracting(frame -> frame[0]).containsExactly((byte) 2, (byte) 3);
    }
}
