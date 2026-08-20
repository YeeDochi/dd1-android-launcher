package com.winlator.dd1;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

// What a save slot holds, in the three terms a person recognises it by: the
// estate's name, the time played, and when it was last saved. Sizes and dates on
// disk say nothing about which of two campaigns is the one you want.
//
// The game's save is DSON. Its data section stores a field as the field's name, a
// NUL, padding to a four-byte boundary, and then the value, which is enough to
// read these three without understanding the rest of the format.
public final class DD1SaveSlot {
    public final String name;
    public final String estate;
    public final float playedSeconds;
    public final String savedAt;

    private DD1SaveSlot(String name, String estate, float playedSeconds, String savedAt) {
        this.name = name;
        this.estate = estate;
        this.playedSeconds = playedSeconds;
        this.savedAt = savedAt;
    }

    public static DD1SaveSlot of(File profileDir) {
        File game = new File(profileDir, "persist.game.json");
        if (!game.isFile() || game.length() == 0) return null;
        byte[] dson = read(game);
        if (dson == null) return null;
        return new DD1SaveSlot(profileDir.getName(), field(dson, "estatename"),
            number(dson, "totalelapsed"), field(dson, "date_time"));
    }

    // A length, then that many bytes of text, the last of which is a NUL.
    public static String field(byte[] dson, String key) {
        int at = valueAt(dson, key);
        if (at < 0 || at + 4 > dson.length) return null;
        int length = ByteBuffer.wrap(dson, at, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
        if (length <= 1 || at + 4 + length > dson.length) return null;
        return new String(dson, at + 4, length - 1, StandardCharsets.UTF_8);
    }

    public static float number(byte[] dson, String key) {
        int at = valueAt(dson, key);
        if (at < 0 || at + 4 > dson.length) return -1f;
        return ByteBuffer.wrap(dson, at, 4).order(ByteOrder.LITTLE_ENDIAN).getFloat();
    }

    // Scanning for the name is a heuristic, and a name that appeared inside some
    // other value would mislead it. It is cheap, it only ever produces a label,
    // and a wrong read shows up as a wrong label rather than as a lost save.
    private static int valueAt(byte[] dson, String key) {
        byte[] needle = (key + "\0").getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i + needle.length <= dson.length; i++) {
            boolean found = true;
            for (int j = 0; j < needle.length; j++) {
                if (dson[i + j] != needle[j]) {
                    found = false;
                    break;
                }
            }
            if (!found) continue;
            int at = i + needle.length;
            while (at % 4 != 0) at++;
            return at;
        }
        return -1;
    }

    private static byte[] read(File file) {
        try (InputStream in = new FileInputStream(file)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int got;
            while ((got = in.read(buffer)) > 0) out.write(buffer, 0, got);
            return out.toByteArray();
        }
        catch (Exception unreadable) {
            return null;
        }
    }
}
