package com.winlator.dd1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class DD1SaveSlotTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void readsTheEstateThePlaytimeAndTheSaveTime() throws IOException {
        File profile = folder.newFolder("profile_0");
        write(new File(profile, "persist.game.json"), dson());

        DD1SaveSlot slot = DD1SaveSlot.of(profile);

        assertEquals("profile_0", slot.name);
        assertEquals("Hamlet", slot.estate);
        assertEquals(616.707f, slot.playedSeconds, 0.01f);
        assertEquals("2026-08-20 02:53:14", slot.savedAt);
    }

    // The cloud hands over bytes rather than a directory, and the same three
    // things have to come out of them so the two sides can be compared.
    @Test
    public void describesASlotFromBytesAlone() throws IOException {
        DD1SaveSlot slot = DD1SaveSlot.of("profile_2", dson());

        assertEquals("profile_2", slot.name);
        assertEquals("Hamlet", slot.estate);
        assertEquals(616.707f, slot.playedSeconds, 0.01f);
    }

    @Test
    public void nothingDescribesNoSlot() {
        assertNull(DD1SaveSlot.of("profile_2", new byte[0]));
        assertNull(DD1SaveSlot.of("profile_2", null));
    }

    // A slot the player has never used has no game file, and inventing a name
    // for it would put an empty row on the screen.
    @Test
    public void anUnusedSlotIsNotASlot() throws IOException {
        assertNull(DD1SaveSlot.of(folder.newFolder("profile_3")));
    }

    // A save this cannot read is still a save: it says so rather than claiming
    // the slot is empty.
    @Test
    public void aSaveWithoutTheseFieldsStillCountsAsOne() throws IOException {
        File profile = folder.newFolder("profile_1");
        write(new File(profile, "persist.game.json"), new byte[] {1, (byte)0xb1, 0, 0, 9, 9});

        DD1SaveSlot slot = DD1SaveSlot.of(profile);

        assertEquals("profile_1", slot.name);
        assertNull(slot.estate);
        assertEquals(-1f, slot.playedSeconds, 0.01f);
        assertNull(slot.savedAt);
    }

    // Built here rather than copied from a real save: the launcher ships no game
    // data and no save data, tests included.
    private static byte[] dson() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(new byte[] {1, (byte)0xb1, 0, 0, 0, 0, 0, 0});
        field(out, "totalelapsed", floatBytes(616.707f));
        field(out, "estatename", text("Hamlet"));
        field(out, "date_time", text("2026-08-20 02:53:14"));
        return out.toByteArray();
    }

    private static void field(ByteArrayOutputStream out, String name, byte[] value)
            throws IOException {
        out.write(name.getBytes("UTF-8"));
        out.write(0);
        while (out.size() % 4 != 0) out.write(0);
        out.write(value);
    }

    private static byte[] floatBytes(float value) {
        return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putFloat(value).array();
    }

    private static byte[] text(String value) throws IOException {
        byte[] body = value.getBytes("UTF-8");
        ByteBuffer buffer = ByteBuffer.allocate(4 + body.length + 1)
            .order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(body.length + 1);
        buffer.put(body);
        buffer.put((byte)0);
        return buffer.array();
    }

    private static void write(File file, byte[] content) throws IOException {
        try (OutputStream out = new FileOutputStream(file)) {
            out.write(content);
        }
    }
}
