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

public class DD1GameLocationTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void theGameDriveFollowsTheGame() {
        String drives = "D:/storage/emulated/0/DownloadE:/data/data/com.winlator/storage"
            + "G:/data/user/0/com.winlator/files/game";
        assertEquals("D:/storage/emulated/0/DownloadE:/data/data/com.winlator/storage"
                + "G:/storage/emulated/0/DD1/game",
            DD1Drives.withGame(drives, "/storage/emulated/0/DD1/game"));
    }

    @Test
    public void aGameDriveIsAddedWhereThereWasNone() {
        assertEquals("D:/dlG:/g", DD1Drives.withGame("D:/dl", "/g"));
        assertEquals("G:/g", DD1Drives.withGame("", "/g"));
    }

    @Test
    public void aNewInstallGoesToStorageAndAnExistingOneStays() throws IOException {
        File fresh = folder.newFolder("fresh");
        assertEquals(DD1GameLocation.STORAGE, DD1GameLocation.defaultFor(fresh));

        File installed = folder.newFolder("installed");
        game(new File(installed, "game"));
        assertEquals(DD1GameLocation.APP, DD1GameLocation.defaultFor(installed));

        File partial = folder.newFolder("partial");
        new File(partial, "staging/game").mkdirs();
        assertEquals(DD1GameLocation.APP, DD1GameLocation.defaultFor(partial));
    }

    @Test
    public void theInstallMovesWithItsModsAndDlcRecord() throws IOException {
        File app = folder.newFolder("app");
        File storage = new File(folder.getRoot(), "storage/DD1");
        game(new File(app, "game"));
        write(new File(app, "game/mods/123/project.xml"), new byte[] {1});
        write(new File(app, DD1DlcVersions.FILE), "580100=aaa\n".getBytes());
        new File(app, "staging/game").mkdirs();
        long[] last = {0, 0};

        DD1GameLocation.move(app, storage, (done, total) -> {
            last[0] = done;
            last[1] = total;
        }, () -> false);

        assertTrue(DD1Game.validate(new File(storage, "game")).valid);
        assertTrue(new File(storage, "game/mods/123/project.xml").isFile());
        assertTrue(new File(storage, DD1DlcVersions.FILE).isFile());
        assertTrue("the old copy waits for the caller", new File(app, "game").exists());

        DD1GameLocation.clearSource(app);
        assertFalse("the old copy is gone", new File(app, "game").exists());
        assertFalse("partial downloads do not follow", new File(app, "staging").exists());
        assertFalse(new File(storage, "game-moving").exists());
        assertEquals(last[1], last[0]);
    }

    // The other build may already have put a game there; it is used, not copied over.
    @Test
    public void aGameAlreadyAtTheDestinationIsAdopted() throws IOException {
        File app = folder.newFolder("app");
        File storage = folder.newFolder("storage");
        game(new File(app, "game"));
        game(new File(storage, "game"));
        write(new File(storage, "game/theirs"), new byte[] {7});

        DD1GameLocation.move(app, storage, (done, total) -> {
            throw new AssertionError("nothing should be copied");
        }, () -> false);
        DD1GameLocation.clearSource(app);

        assertTrue(new File(storage, "game/theirs").isFile());
        assertFalse(new File(app, "game").exists());
    }

    @Test
    public void aStoppedMoveLeavesTheGameWhereItWas() throws IOException {
        File app = folder.newFolder("app");
        File storage = folder.newFolder("storage");
        game(new File(app, "game"));

        DD1GameLocation.move(app, storage, (done, total) -> {}, () -> true);

        assertTrue(DD1Game.validate(new File(app, "game")).valid);
        assertFalse(new File(storage, "game").exists());
        assertFalse(new File(storage, "game-moving").exists());
    }

    private static void game(File dir) throws IOException {
        for (String path : new String[] {"audio", "campaign", "dungeons", "heroes", "shared"})
            new File(dir, path).mkdirs();
        write(new File(dir, "_windows/win64/Darkest.exe"), new byte[] {'M', 'Z', (byte)0x90, 0});
    }

    private static void write(File file, byte[] content) throws IOException {
        file.getParentFile().mkdirs();
        try (OutputStream out = new FileOutputStream(file)) {
            out.write(content);
        }
    }
}
