package com.winlator.dd1;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class DD1SavePolicyTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void thereIsNothingToTakeWithoutASave() throws IOException {
        assertFalse(DD1SavePolicy.worthTaking(folder.newFolder()));
    }

    @Test
    public void aSaveWithNoSnapshotIsWorthTaking() throws IOException {
        File files = folder.newFolder();
        save(files, "abc");

        assertTrue(DD1SavePolicy.worthTaking(files));
    }

    // Launching the game twice without playing must not spend a snapshot slot,
    // or three launches would push out the state worth going back to.
    @Test
    public void aSaveAlreadySnapshottedIsNotWorthTakingAgain() throws IOException {
        File files = folder.newFolder();
        save(files, "abc");
        DD1SaveSnapshots.take(files, 1000L);

        assertFalse(DD1SavePolicy.worthTaking(files));
    }

    @Test
    public void aSaveThatMovedOnIsWorthTakingAgain() throws IOException {
        File files = folder.newFolder();
        save(files, "abc");
        DD1SaveSnapshots.take(files, 1000L);
        save(files, "played some more");

        assertTrue(DD1SavePolicy.worthTaking(files));
    }

    private static void save(File files, String content) throws IOException {
        File dir = new File(DD1Saves.root(files), "profile_0");
        dir.mkdirs();
        try (OutputStream out = new FileOutputStream(new File(dir, "persist.game.json"))) {
            out.write(content.getBytes("UTF-8"));
        }
    }
}
