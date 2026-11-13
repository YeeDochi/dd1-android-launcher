package com.winlator.dd1;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

// The build that installs beside Winlator lives under a different package name,
// and Winlator's root filesystem names its own home - /data/data/com.winlator -
// inside box64, wineserver, ntdll, ld.so.cache and some 160 other files, in
// fixed-size fields. A new name of the same length can be written over the old
// one byte for byte without moving anything else, so every file the runtime
// unpacks passes through here on its way to disk.
//
// Only a path is rewritten: the name has to follow a '/' (Unix) or a '\'
// (Wine's registry), so nothing that merely mentions the name is touched.
public final class DD1Relocation {
    static final String ORIGINAL = "com.winlator";

    private DD1Relocation() {}

    public static String packageName() {
        return com.winlator.BuildConfig.APPLICATION_ID;
    }

    static boolean active(String target) {
        if (ORIGINAL.equals(target)) return false;
        if (target.length() != ORIGINAL.length())
            throw new IllegalStateException("Package name must be " + ORIGINAL.length()
                + " characters to replace " + ORIGINAL + " in place: " + target);
        return true;
    }

    public static OutputStream wrap(OutputStream out) {
        return wrap(out, packageName());
    }

    static OutputStream wrap(OutputStream out, String target) {
        return active(target) ? new Rewriting(out, target) : out;
    }

    public static String link(String linkName) {
        return link(linkName, packageName());
    }

    static String link(String linkName, String target) {
        if (linkName == null || !active(target)) return linkName;
        byte[] bytes = linkName.getBytes(StandardCharsets.UTF_8);
        rewrite(bytes, bytes.length, ORIGINAL.getBytes(StandardCharsets.US_ASCII),
            target.getBytes(StandardCharsets.US_ASCII));
        return new String(bytes, StandardCharsets.UTF_8);
    }

    // Replaces every separator-prefixed match that ends before limit. A match
    // starting inside the last name-length bytes cannot be complete yet, so the
    // stream keeps that many back and looks again once more has arrived.
    static void rewrite(byte[] data, int limit, byte[] from, byte[] to) {
        for (int i = 1; i + from.length <= limit; i++) {
            byte before = data[i - 1];
            if (before != '/' && before != '\\') continue;
            boolean match = true;
            for (int j = 0; j < from.length; j++) {
                if (data[i + j] != from[j]) {
                    match = false;
                    break;
                }
            }
            if (!match) continue;
            System.arraycopy(to, 0, data, i, to.length);
            i += from.length - 1;
        }
    }

    private static final class Rewriting extends FilterOutputStream {
        private final byte[] from;
        private final byte[] to;
        // The separator plus the name, held back between writes.
        private final int keep;
        private byte[] buffer = new byte[64 * 1024];
        private int length;

        Rewriting(OutputStream out, String target) {
            super(out);
            from = ORIGINAL.getBytes(StandardCharsets.US_ASCII);
            to = target.getBytes(StandardCharsets.US_ASCII);
            keep = from.length;
        }

        @Override
        public void write(int b) throws IOException {
            write(new byte[] {(byte)b}, 0, 1);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            if (length + len > buffer.length) {
                byte[] grown = new byte[Math.max(buffer.length * 2, length + len)];
                System.arraycopy(buffer, 0, grown, 0, length);
                buffer = grown;
            }
            System.arraycopy(b, off, buffer, length, len);
            length += len;
            if (length <= keep) return;
            rewrite(buffer, length, from, to);
            int ready = length - keep;
            out.write(buffer, 0, ready);
            System.arraycopy(buffer, ready, buffer, 0, keep);
            length = keep;
        }

        @Override
        public void flush() throws IOException {
            out.flush();
        }

        @Override
        public void close() throws IOException {
            rewrite(buffer, length, from, to);
            out.write(buffer, 0, length);
            length = 0;
            super.close();
        }
    }
}
