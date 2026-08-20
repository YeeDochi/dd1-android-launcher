package com.winlator.dd1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class DD1SaveStagingTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void aStagedFileLandsUnderTheSlot() throws IOException {
        File files = folder.newFolder();

        assertTrue(DD1SaveStaging.put(files, "profile_0",
            "profile_0/persist.game.json", new byte[] {1, 2, 3}));

        assertEquals(3,
            new File(files, "staging/saves/profile_0/persist.game.json").length());
    }

    // An empty file is what a transfer that failed leaves behind, and it must
    // never reach the point where it could replace a save.
    @Test
    public void anEmptyFileIsRefused() throws IOException {
        File files = folder.newFolder();

        assertFalse(DD1SaveStaging.put(files, "profile_0",
            "profile_0/persist.game.json", new byte[0]));
    }

    @Test
    public void aPathOutsideTheSlotIsRefused() throws IOException {
        File files = folder.newFolder();

        assertFalse(DD1SaveStaging.put(files, "profile_0",
            "profile_1/persist.game.json", new byte[] {1}));
        assertFalse(DD1SaveStaging.put(files, "profile_0",
            "profile_0/../escape.json", new byte[] {1}));
    }

    // Replacing a slot is the one step that can lose a campaign, so it takes a
    // snapshot first and gives up if it cannot have one.
    @Test
    public void applyingReplacesTheSlotAndSnapshotsFirst() throws IOException {
        File files = folder.newFolder();
        File live = new File(DD1Saves.root(files), "profile_0");
        live.mkdirs();
        write(new File(live, "persist.game.json"), "old save".getBytes());
        write(new File(live, "persist.stale.json"), "gone".getBytes());
        DD1SaveStaging.put(files, "profile_0", "profile_0/persist.game.json",
            "new save".getBytes());

        assertTrue(DD1SaveStaging.apply(files, "profile_0"));

        assertEquals(8, new File(live, "persist.game.json").length());
        assertFalse(new File(live, "persist.stale.json").exists());
        assertEquals(1, DD1SaveSnapshots.kept(files).size());
    }

    // Nothing staged means nothing to apply, and wiping a real slot for it would
    // be the whole disaster in one step.
    @Test
    public void applyingNothingIsRefused() throws IOException {
        File files = folder.newFolder();
        File live = new File(DD1Saves.root(files), "profile_0");
        live.mkdirs();
        write(new File(live, "persist.game.json"), "old save".getBytes());

        assertFalse(DD1SaveStaging.apply(files, "profile_0"));

        assertEquals(8, new File(live, "persist.game.json").length());
    }

    private static void write(File file, byte[] content) throws IOException {
        file.getParentFile().mkdirs();
        try (OutputStream out = new FileOutputStream(file)) {
            out.write(content);
        }
    }
}
