package com.winlator.dd1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

public class DD1FoldersTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void aChosenDD1FolderHoldsTheMods() {
        File def = new File("/sdcard/DD1");
        assertEquals(new File("/sdcard/Games/DD1", "mods"),
            DD1Folders.mods("/sdcard/Games/DD1", "/sdcard/OldMods", def));
    }

    // Chosen before there was a DD1 folder: still read as the mods folder.
    @Test
    public void aModsFolderChosenBeforeIsStillRead() {
        assertEquals(new File("/sdcard/OldMods"),
            DD1Folders.mods(null, "/sdcard/OldMods", new File("/sdcard/DD1")));
    }

    @Test
    public void nothingChosenIsDD1Mods() {
        assertEquals(new File("/sdcard/DD1", "mods"),
            DD1Folders.mods(null, null, new File("/sdcard/DD1")));
    }

    @Test
    public void theOldOverridesFolderBecomesDll() throws Exception {
        File base = temp.newFolder("DD1");
        File overrides = new File(base, "overrides/_windows/win64");
        assertTrue(overrides.mkdirs());
        assertTrue(new File(overrides, "dinput8.dll").createNewFile());
        File dll = DD1Folders.dll(base);
        assertEquals(new File(base, "dll"), dll);
        assertTrue(new File(dll, "_windows/win64/dinput8.dll").isFile());
        assertFalse(new File(base, "overrides").exists());
    }

    // A dll folder already there is never overwritten by the old one.
    @Test
    public void anExistingDllFolderIsLeftAlone() throws Exception {
        File base = temp.newFolder("DD1");
        assertTrue(new File(base, "dll").mkdirs());
        assertTrue(new File(base, "overrides").mkdirs());
        DD1Folders.dll(base);
        assertTrue(new File(base, "overrides").isDirectory());
    }
}
