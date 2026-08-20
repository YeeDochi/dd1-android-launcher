package com.winlator.dd1;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.util.zip.Deflater;

public class DD1CloudTransferTest {
    @Test
    public void aBlockRequestBecomesAUrl() {
        assertEquals("https://host.example/path?token=1",
            DD1CloudTransfer.url("host.example", "/path?token=1", true));
        assertEquals("http://host.example/path",
            DD1CloudTransfer.url("host.example", "/path", false));
    }

    // Steam gives the digest it expects, and a file whose bytes do not match it
    // has no business anywhere near a save tree.
    @Test
    public void contentIsCheckedAgainstTheDigestSteamGave() {
        byte[] content = "abc".getBytes();
        byte[] sha1 = new byte[] {
            (byte)0xa9, (byte)0x99, (byte)0x3e, (byte)0x36, (byte)0x47,
            (byte)0x06, (byte)0x81, (byte)0x6a, (byte)0xba, (byte)0x3e,
            (byte)0x25, (byte)0x71, (byte)0x78, (byte)0x50, (byte)0xc2,
            (byte)0x6c, (byte)0x9c, (byte)0xd0, (byte)0xd8, (byte)0x9d};

        assertTrue(DD1CloudTransfer.digestMatches(content, sha1));
        assertFalse(DD1CloudTransfer.digestMatches("abd".getBytes(), sha1));
    }

    @Test
    public void anUncompressedBodyIsLeftAlone() {
        byte[] content = "hello".getBytes();

        assertArrayEquals(content, DD1CloudTransfer.inflate(content, content.length));
    }

    @Test
    public void aCompressedBodyIsInflatedToItsRawSize() {
        byte[] content = "hello hello hello hello".getBytes();
        byte[] squashed = deflate(content);

        assertArrayEquals(content, DD1CloudTransfer.inflate(squashed, content.length));
    }

    private static byte[] deflate(byte[] content) {
        Deflater deflater = new Deflater();
        deflater.setInput(content);
        deflater.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[256];
        while (!deflater.finished()) out.write(buffer, 0, deflater.deflate(buffer));
        deflater.end();
        return out.toByteArray();
    }
}
