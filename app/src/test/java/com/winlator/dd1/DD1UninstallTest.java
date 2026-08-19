package com.winlator.dd1;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;

public class DD1UninstallTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void removesTheGameAndAnyHalfFinishedDownload() throws IOException {
        File files = folder.newFolder("files");
        file(files, "game/_windowsnosteam/win64/Darkest.exe");
        file(files, "game/mods/1234/mod.xml");
        file(files, "staging/game/audio/sound.bank");
        File prefix = file(files, "rootfs/home/xuser-1/.wine/system.reg");

        assertTrue(DD1Installer.uninstall(files));

        assertFalse(new File(files, "game").exists());
        assertFalse(new File(files, "staging").exists());
        assertTrue("the Wine prefix holds the saves", prefix.isFile());
    }

    @Test
    public void succeedsWhenThereIsNothingToRemove() throws IOException {
        assertTrue(DD1Installer.uninstall(folder.newFolder("files")));
    }

    private static File file(File root, String path) throws IOException {
        File target = new File(root, path);
        target.getParentFile().mkdirs();
        target.createNewFile();
        return target;
    }
}
