package com.winlator.dd1;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class DD1ProfileRepairTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    // What the launcher hands over on a device, without needing Android here.
    private static final java.util.function.IntFunction<String> CONFIG =
        id -> "{\"id\":" + id + ",\"graphicsDriver\":\"turnip,gladio\"}";

    private File home() {
        File home = new File(folder.getRoot(), "rootfs/home");
        home.mkdirs();
        return home;
    }

    private static File profile(File home, String name) {
        File dir = new File(home, name);
        dir.mkdirs();
        return dir;
    }

    private static void write(File file, String text) throws Exception {
        file.getParentFile().mkdirs();
        Files.write(file.toPath(), text.getBytes(StandardCharsets.UTF_8));
    }

    private static void save(File profileDir, String slot) throws Exception {
        write(new File(DD1Saves.saveTreeIn(profileDir), slot + "/persist.game.json"), "{}");
    }

    @Test
    public void aProfileWithItsConfigIsLeftAlone() throws Exception {
        File home = home();
        File dir = profile(home, "xuser-1");
        write(new File(dir, ".container"), "{\"id\":1,\"name\":\"kept\"}");

        DD1ProfileRepair.repair(folder.getRoot(), CONFIG);

        assertTrue(dir.isDirectory());
        assertTrue(new String(Files.readAllBytes(new File(dir, ".container").toPath()),
            StandardCharsets.UTF_8).contains("kept"));
    }

    // The window between mkdirs() and saveData() is a tar extraction. Killed in
    // there, the directory holds a half-unpacked prefix and nothing a player could
    // have saved, and every launch afterwards died reading the config that is not
    // there.
    @Test
    public void aProfileThatNeverFinishedBeingMadeIsRemoved() throws Exception {
        File home = home();
        File dir = profile(home, "xuser-1");
        write(new File(dir, ".wine/drive_c/windows/half-extracted"), "x");

        DD1ProfileRepair.repair(folder.getRoot(), CONFIG);

        assertFalse("nothing in it was worth keeping", dir.exists());
    }

    // The same missing config over a prefix the game has played in: the saves are
    // in there, so the config is written back rather than the tree thrown away.
    @Test
    public void aProfileHoldingSavesGetsItsConfigBack() throws Exception {
        File home = home();
        File dir = profile(home, "xuser-1");
        save(dir, "profile_0");

        DD1ProfileRepair.repair(folder.getRoot(), CONFIG);

        assertTrue(dir.isDirectory());
        File config = new File(dir, ".container");
        assertTrue("a config exists again", config.isFile());
        String written = new String(Files.readAllBytes(config.toPath()), StandardCharsets.UTF_8);
        assertTrue("it is this profile's id", written.contains("\"id\":1"));
        assertTrue("and this device's driver", written.contains("turnip,gladio"));
        assertTrue("the save survived",
            new File(DD1Saves.saveTreeIn(dir), "profile_0/persist.game.json").isFile());
    }

    // A crash during saveData truncates rather than deletes: the file opens for
    // writing before the bytes arrive. That did not crash, but it did hide a
    // profile, so it is repaired the same way.
    @Test
    public void anEmptyConfigIsRepairedToo() throws Exception {
        File home = home();
        File dir = profile(home, "xuser-2");
        save(dir, "profile_0");
        write(new File(dir, ".container"), "");

        DD1ProfileRepair.repair(folder.getRoot(), CONFIG);

        String written = new String(Files.readAllBytes(new File(dir, ".container").toPath()),
            StandardCharsets.UTF_8);
        assertTrue(written.contains("\"id\":2"));
    }

    @Test
    public void anythingThatIsNotAProfileDirectoryIsNotTouched() throws Exception {
        File home = home();
        File stray = profile(home, "not-a-profile");
        File file = new File(home, "xuser-notanumber");
        write(file, "x");

        DD1ProfileRepair.repair(folder.getRoot(), CONFIG);

        assertTrue(stray.isDirectory());
        assertTrue(file.isFile());
    }

    @Test
    public void noRuntimeYetIsNotAnError() {
        DD1ProfileRepair.repair(folder.getRoot(), CONFIG);
    }
}
