package com.winlator.dd1;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class DD1RelocationTest {
    private static final String TARGET = "com.dd1.wine";

    @Test
    public void theStandardBuildWritesFilesUntouched() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertSame(out, DD1Relocation.wrap(out, "com.winlator"));
        assertEquals("/data/data/com.winlator/x",
            DD1Relocation.link("/data/data/com.winlator/x", "com.winlator"));
    }

    @Test(expected = IllegalStateException.class)
    public void aNameOfAnotherLengthIsRefused() {
        DD1Relocation.wrap(new ByteArrayOutputStream(), "com.dd1");
    }

    @Test
    public void unixAndRegistryPathsAreRewrittenAndNothingElse() throws IOException {
        String in = "/data/data/com.winlator/files\0Z:\\\\data\\\\data\\\\com.winlator\\\\x"
            + " package com.winlator.dd1";
        String expected = "/data/data/com.dd1.wine/files\0Z:\\\\data\\\\data\\\\com.dd1.wine\\\\x"
            + " package com.winlator.dd1";
        assertEquals(expected, through(in.getBytes(StandardCharsets.UTF_8), 64 * 1024));
    }

    // The tar copy hands the stream whatever chunk size it reads, so a path cut
    // in two by a chunk boundary has to come out rewritten all the same.
    @Test
    public void aPathSplitAcrossWritesIsStillRewritten() throws IOException {
        StringBuilder in = new StringBuilder();
        StringBuilder expected = new StringBuilder();
        for (int i = 0; i < 200; i++) {
            in.append("abc/data/data/com.winlator/lib").append(i).append('\n');
            expected.append("abc/data/data/com.dd1.wine/lib").append(i).append('\n');
        }
        byte[] bytes = in.toString().getBytes(StandardCharsets.UTF_8);
        for (int chunk = 1; chunk <= 29; chunk++)
            assertEquals("chunk " + chunk, expected.toString(), through(bytes, chunk));
    }

    @Test
    public void binaryContentKeepsItsLength() throws IOException {
        byte[] elf = new byte[4096];
        for (int i = 0; i < elf.length; i++) elf[i] = (byte)(i * 31);
        byte[] path = "/data/data/com.winlator/files/rootfs/lib/ld.so".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(path, 0, elf, 1000, path.length);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (OutputStream stream = DD1Relocation.wrap(out, TARGET)) {
            stream.write(elf, 0, 1003);
            stream.write(elf, 1003, elf.length - 1003);
        }
        byte[] result = out.toByteArray();
        assertEquals(elf.length, result.length);
        byte[] expected = elf.clone();
        byte[] moved = "/data/data/com.dd1.wine/files/rootfs/lib/ld.so".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(moved, 0, expected, 1000, moved.length);
        assertArrayEquals(expected, result);
    }

    @Test
    public void symlinkTargetsAreRewritten() {
        assertEquals("/data/data/com.dd1.wine/files/imagefs",
            DD1Relocation.link("/data/data/com.winlator/files/imagefs", TARGET));
        assertEquals("../lib/libc.so", DD1Relocation.link("../lib/libc.so", TARGET));
    }

    private static String through(byte[] bytes, int chunk) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (OutputStream stream = DD1Relocation.wrap(out, TARGET)) {
            for (int i = 0; i < bytes.length; i += chunk)
                stream.write(bytes, i, Math.min(chunk, bytes.length - i));
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }
}
