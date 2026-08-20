package com.winlator.dd1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

public class DD1SaveSnapshotsTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void aSnapshotCopiesTheSaveWithoutTouchingIt() throws IOException {
        File files = folder.newFolder();
        save(files, "abc");

        File snapshot = DD1SaveSnapshots.take(files, 1000L);

        assertEquals("1000", snapshot.getName());
        assertTrue(new File(snapshot, "profile_0/persist.game.json").isFile());
        assertTrue(new File(DD1Saves.root(files), "profile_0/persist.game.json").isFile());
    }

    @Test
    public void thereIsNothingToSnapshotBeforeTheGameHasSaved() throws IOException {
        assertNull(DD1SaveSnapshots.take(folder.newFolder(), 1000L));
    }

    // Three is the whole point of the ring: the fourth pushes the first out
    // rather than filling the phone.
    @Test
    public void onlyTheLatestThreeAreKept() throws IOException {
        File files = folder.newFolder();
        save(files, "abc");

        DD1SaveSnapshots.take(files, 1000L);
        DD1SaveSnapshots.take(files, 2000L);
        DD1SaveSnapshots.take(files, 3000L);
        DD1SaveSnapshots.take(files, 4000L);

        List<File> kept = DD1SaveSnapshots.kept(files);
        assertEquals(3, kept.size());
        assertEquals("4000", kept.get(0).getName());
        assertEquals("3000", kept.get(1).getName());
        assertEquals("2000", kept.get(2).getName());
    }

    @Test
    public void takingTheSameMomentTwiceReplacesRatherThanDuplicates() throws IOException {
        File files = folder.newFolder();
        save(files, "first");
        DD1SaveSnapshots.take(files, 1000L);
        save(files, "second");

        File snapshot = DD1SaveSnapshots.take(files, 1000L);

        assertEquals(1, DD1SaveSnapshots.kept(files).size());
        assertEquals(6, new File(snapshot, "profile_0/persist.game.json").length());
    }

    private static void save(File files, String content) throws IOException {
        File dir = new File(DD1Saves.root(files), "profile_0");
        dir.mkdirs();
        try (OutputStream out = new FileOutputStream(new File(dir, "persist.game.json"))) {
            out.write(content.getBytes("UTF-8"));
        }
    }
}
