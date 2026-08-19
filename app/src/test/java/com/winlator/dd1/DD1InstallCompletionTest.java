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

public class DD1InstallCompletionTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void anInterruptedDownloadIsNotPromotedToAnInstall() throws IOException {
        File files = staging();

        assertFalse("the downloader never said it finished",
            DD1Installer.activate(files).success);
        assertFalse(new File(files, "game").exists());
    }

    @Test
    public void aFinishedDownloadIsPromoted() throws IOException {
        File files = staging();
        DD1Installer.markDownloadComplete(files);

        assertTrue(DD1Installer.activate(files).success);
        assertTrue(new File(files, "game/_windows/win64/Darkest.exe").isFile());
    }

    @Test
    public void theMarkIsClearedSoTheNextDownloadMustEarnItAgain() throws IOException {
        File files = staging();
        DD1Installer.markDownloadComplete(files);
        DD1Installer.activate(files);

        File second = staging();
        assertFalse(DD1Installer.activate(second).success);
    }

    private File staging() throws IOException {
        File files = folder.newFolder();
        File game = new File(files, "staging/game");
        for (String directory : new String[] {"audio", "campaign", "dungeons", "heroes", "shared"})
            new File(game, directory).mkdirs();
        File executable = new File(game, "_windows/win64/Darkest.exe");
        executable.getParentFile().mkdirs();
        try (OutputStream out = new FileOutputStream(executable)) {
            out.write(new byte[] {'M', 'Z', (byte)0x90, 0});
        }
        return files;
    }
}
