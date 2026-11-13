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

    // An interrupted download is resumed, not thrown away - starting the 4 GB over
    // on every stall is what made a slow connection hopeless. What keeps a
    // half-written file from being trusted is the downloader's depot record:
    // with it gone, every chunk on disk is checked against the manifest.
    @Test
    public void anInterruptedDownloadIsResumedWithEveryDepotCheckedAgain() throws IOException {
        File files = staging();
        DD1Installer.beginDownload(files);
        assertTrue("the attempt is on record",
            new File(files, "staging/download-started").isFile());

        File leftover = new File(files, "staging/game/_windows/win64/Darkest.exe");
        File record = new File(files, "staging/game/" + DD1Installer.DEPOT_RECORD);
        record.getParentFile().mkdirs();
        assertTrue(record.createNewFile());
        DD1Installer.beginDownload(files);

        assertTrue("what arrived is kept", leftover.isFile());
        assertFalse("no depot is taken on its word", record.exists());
    }

    @Test
    public void aRepairChecksTheInstalledGameInPlace() throws IOException {
        File files = staging();
        DD1Installer.markDownloadComplete(files);
        assertTrue(DD1Installer.activate(files).success);
        File record = new File(files, "game/" + DD1Installer.DEPOT_RECORD);
        record.getParentFile().mkdirs();
        assertTrue(record.createNewFile());

        assertTrue(DD1Installer.beginRepair(files).equals(new File(files, "game")));
        assertFalse(record.exists());
        assertTrue(DD1Installer.checkRepaired(files).success);
    }

    // Mods live inside the game tree, and replacing the tree took them with it:
    // every Workshop mod was fetched again after a reinstall or an update.
    @Test
    public void modsSurviveTheGameBeingReplaced() throws IOException {
        File files = staging();
        File installed = new File(files, "game");
        new File(installed, "mods/12345").mkdirs();
        new File(installed, "mods-disabled/678").mkdirs();
        assertTrue(new File(installed, "mods/12345/project.xml").createNewFile());
        DD1Installer.markDownloadComplete(files);

        assertTrue(DD1Installer.activate(files).success);
        assertTrue(new File(files, "game/mods/12345/project.xml").isFile());
        assertTrue(new File(files, "game/mods-disabled/678").isDirectory());
        assertTrue(new File(files, "game/_windows/win64/Darkest.exe").isFile());
    }

    @Test
    public void aDownloadThatBecameAnInstallLeavesNothingToDiscard() throws IOException {
        File files = staging();
        DD1Installer.beginDownload(files);
        DD1Installer.markDownloadComplete(files);
        assertTrue(DD1Installer.activate(files).success);

        assertFalse("nothing is left on record for the next download to discard",
            new File(files, "staging/download-started").exists());
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
