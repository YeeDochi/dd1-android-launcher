package com.winlator.dd1;

import static org.junit.Assert.assertEquals;

import java.io.File;

import org.junit.Test;

public class DD1WorkshopCdnTest {
    @Test
    public void targetCannotEscapeStagingDirectory() {
        // Absolute on every host: on Windows "/tmp" has no drive and resolves
        // against the current one, so the expectation has to as well.
        File root = new File("/tmp/workshop").getAbsoluteFile();

        assertEquals(new File(root, "heroes/a.txt").toPath(),
            DD1WorkshopCdn.safeTarget(root, "heroes\\a.txt"));
        try {
            DD1WorkshopCdn.safeTarget(root, "../game.exe");
            throw new AssertionError("path traversal accepted");
        }
        catch (IllegalArgumentException expected) {}
    }

    @Test
    public void encryptedChunkBufferFitsCompressedOverhead() {
        assertEquals(120, DD1WorkshopCdn.bufferSize(100, 120));
        assertEquals(120, DD1WorkshopCdn.bufferSize(120, 100));
    }

    @Test
    public void retryDelayGrowsButStaysBounded() {
        assertEquals(250L, DD1WorkshopCdn.retryDelay(0));
        assertEquals(2000L, DD1WorkshopCdn.retryDelay(99));
    }
}
