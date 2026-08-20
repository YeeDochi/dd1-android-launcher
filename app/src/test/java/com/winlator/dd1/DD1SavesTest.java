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
import java.util.List;

public class DD1SavesTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void theSaveRootIsInsideTheWinePrefix() throws IOException {
        File files = folder.newFolder();

        assertEquals(new File(files,
                "rootfs/home/xuser-1/.wine/drive_c/users/xuser/Documents/Darkest"),
            DD1Saves.root(files));
    }

    // An empty profile_0 is what a prefix that has never run the game looks
    // like, and calling that a save would offer to snapshot nothing.
    @Test
    public void aProfileWithoutAGameFileIsNotASave() throws IOException {
        File files = folder.newFolder();
        new File(DD1Saves.root(files), "profile_0").mkdirs();

        assertFalse(DD1Saves.isSaveTree(DD1Saves.root(files)));
        assertEquals(0, DD1Saves.profiles(DD1Saves.root(files)).size());
    }

    @Test
    public void aProfileWithAGameFileIsASave() throws IOException {
        File files = folder.newFolder();
        profile(files, 0, "{\"x\":1}");

        assertTrue(DD1Saves.isSaveTree(DD1Saves.root(files)));
        List<File> profiles = DD1Saves.profiles(DD1Saves.root(files));
        assertEquals(1, profiles.size());
        assertEquals("profile_0", profiles.get(0).getName());
    }

    @Test
    public void anEmptyGameFileIsNotASave() throws IOException {
        File files = folder.newFolder();
        profile(files, 0, "");

        assertFalse(DD1Saves.isSaveTree(DD1Saves.root(files)));
    }

    // The game numbers its slots and the screen will list them, so ten sorts
    // after two rather than between one and three.
    @Test
    public void profilesComeBackInSlotOrder() throws IOException {
        File files = folder.newFolder();
        profile(files, 10, "{}");
        profile(files, 2, "{}");
        profile(files, 1, "{}");

        List<File> profiles = DD1Saves.profiles(DD1Saves.root(files));

        assertEquals("profile_1", profiles.get(0).getName());
        assertEquals("profile_2", profiles.get(1).getName());
        assertEquals("profile_10", profiles.get(2).getName());
    }

    @Test
    public void aMissingRootIsNotAnError() throws IOException {
        File files = folder.newFolder();

        assertFalse(DD1Saves.isSaveTree(DD1Saves.root(files)));
        assertEquals(0, DD1Saves.profiles(DD1Saves.root(files)).size());
    }

    private static void profile(File files, int slot, String game) throws IOException {
        File dir = new File(DD1Saves.root(files), "profile_" + slot);
        dir.mkdirs();
        try (OutputStream out = new FileOutputStream(new File(dir, "persist.game.json"))) {
            out.write(game.getBytes("UTF-8"));
        }
    }
}
