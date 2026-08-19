package com.winlator.dd1;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;

import org.junit.Test;

public class DD1InstallerTest {
    @Test
    public void invalidStagingNeverReplacesInstalledGame() throws Exception {
        File files = Files.createTempDirectory("dd1-install").toFile();
        File active = new File(files, "game");
        active.mkdirs();
        Files.write(new File(active, "marker").toPath(), new byte[]{1});
        new File(files, "staging/game").mkdirs();

        assertFalse(DD1Installer.activate(files).success);
        assertTrue(new File(active, "marker").isFile());
    }

    @Test
    public void validStagingReplacesInstalledGame() throws Exception {
        File files = Files.createTempDirectory("dd1-valid-install").toFile();
        File staging = new File(files, "staging/game");
        File executable = new File(staging, "_windows/win64/Darkest.exe");
        executable.getParentFile().mkdirs();
        writeExecutable(executable);
        DD1Installer.markDownloadComplete(files);
        for (String path : Arrays.asList("audio", "campaign", "dungeons", "heroes", "shared"))
            new File(staging, path).mkdirs();

        assertTrue(DD1Installer.activate(files).success);
        assertTrue(new File(files, "game/_windows/win64/Darkest.exe").isFile());
        assertFalse(staging.exists());
    }

    // Validation reads the PE signature, so fixtures write a real header.
    private static void writeExecutable(File file) throws Exception {
        try (java.io.OutputStream out = new java.io.FileOutputStream(file)) {
            out.write(new byte[] {'M', 'Z', (byte)0x90, 0});
        }
    }
}
