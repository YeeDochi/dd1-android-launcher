package com.winlator.dd1;

import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

// Steam does not move save bytes itself: it hands out the request to make and
// the digest to expect. This is the part that speaks HTTP's language without
// knowing anything about saves.
public final class DD1CloudTransfer {
    private DD1CloudTransfer() {}

    public static String url(String host, String path, boolean useHttps) {
        return (useHttps ? "https://" : "http://") + host + path;
    }

    // The digest Steam gave is the only reason to trust bytes that arrived over
    // someone else's connection.
    public static boolean digestMatches(byte[] content, byte[] expectedSha1) {
        if (expectedSha1 == null || expectedSha1.length == 0) return false;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            return Arrays.equals(digest.digest(content), expectedSha1);
        }
        catch (NoSuchAlgorithmException impossible) {
            return false;
        }
    }

    // Steam reports both sizes; a body already the raw length was never squashed.
    public static byte[] inflate(byte[] body, int rawSize) {
        if (body.length == rawSize) return body;
        Inflater inflater = new Inflater();
        inflater.setInput(body);
        ByteArrayOutputStream out = new ByteArrayOutputStream(rawSize);
        byte[] buffer = new byte[8192];
        try {
            while (!inflater.finished()) {
                int read = inflater.inflate(buffer);
                if (read == 0) break;
                out.write(buffer, 0, read);
            }
        }
        catch (DataFormatException notCompressed) {
            return body;
        }
        finally {
            inflater.end();
        }
        return out.toByteArray();
    }
}
