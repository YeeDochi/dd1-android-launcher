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
import java.util.Collections;
import java.util.Map;

public class DD1DlcMergeTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void aStagedDlcLandsInTheInstalledGame() throws IOException {
        File files = folder.newFolder();
        staged(files, "580100_crimson_court", new byte[] {'D', 'D', 1, 0});

        assertTrue(DD1Installer.merge(files, gid(580100, "aaa")).success);

        assertTrue(new File(files, "game/dlc/580100_crimson_court/content.dat").isFile());
        assertEquals("aaa", DD1DlcVersions.installed(files).get(580100));
    }

    // The downloader allocates every file before it fetches any content, so a
    // folder of the right size holding nothing but zeros is exactly what a
    // download that never delivered looks like. This is the check that kept a
    // 3.7 GB install of zeros off the phone; a DLC deserves the same one.
    @Test
    public void aStagedDlcOfNothingButZerosIsRefused() throws IOException {
        File files = folder.newFolder();
        staged(files, "580100_crimson_court", new byte[64]);

        assertFalse(DD1Installer.merge(files, gid(580100, "aaa")).success);

        assertFalse(new File(files, "game/dlc/580100_crimson_court").exists());
        assertTrue(DD1DlcVersions.installed(files).isEmpty());
    }

    // An update is the same act as an install: the folder Steam ships replaces
    // the one on disk rather than being poured on top of it.
    @Test
    public void anUpdateReplacesWhatWasThere() throws IOException {
        File files = folder.newFolder();
        File old = new File(files, "game/dlc/580100_crimson_court");
        old.mkdirs();
        try (OutputStream out = new FileOutputStream(new File(old, "stale.dat"))) {
            out.write(new byte[] {1});
        }
        staged(files, "580100_crimson_court", new byte[] {'D', 'D', 1, 0});

        assertTrue(DD1Installer.merge(files, gid(580100, "new")).success);

        assertTrue(new File(files, "game/dlc/580100_crimson_court/content.dat").isFile());
        assertFalse(new File(files, "game/dlc/580100_crimson_court/stale.dat").exists());
    }

    @Test
    public void contentTheDownloadNeverDeliveredIsRefusedByName() throws IOException {
        File files = folder.newFolder();
        new File(files, "staging/game/dlc").mkdirs();

        DD1Installer.Result result = DD1Installer.merge(files, gid(580100, "aaa"));

        assertFalse(result.success);
        assertTrue(result.error.contains("580100"));
    }

    // Each DLC is separate content, so a later failure has no business undoing
    // an earlier success.
    @Test
    public void oneFailureLeavesWhatAlreadyLandedAlone() throws IOException {
        File files = folder.newFolder();
        staged(files, "580100_crimson_court", new byte[] {'D', 'D', 1, 0});
        java.util.LinkedHashMap<Integer, String> both = new java.util.LinkedHashMap<>();
        both.put(580100, "aaa");
        both.put(735730, "bbb");

        assertFalse(DD1Installer.merge(files, both).success);

        assertTrue(new File(files, "game/dlc/580100_crimson_court/content.dat").isFile());
        assertEquals("aaa", DD1DlcVersions.installed(files).get(580100));
        assertEquals(1, DD1DlcVersions.installed(files).size());
    }

    private static void staged(File files, String folderName, byte[] content) throws IOException {
        File dlc = new File(files, "staging/game/dlc/" + folderName);
        dlc.mkdirs();
        new File(files, "game/dlc").mkdirs();
        try (OutputStream out = new FileOutputStream(new File(dlc, "content.dat"))) {
            out.write(content);
        }
    }

    private static Map<Integer, String> gid(int appId, String manifest) {
        return Collections.singletonMap(appId, manifest);
    }
}
