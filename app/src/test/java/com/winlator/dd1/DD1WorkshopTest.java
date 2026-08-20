package com.winlator.dd1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

public class DD1WorkshopTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void markerMakesADirectoryAWorkshopInstall() throws Exception {
        File files = folder.newFolder();
        File mod = mkdir(files, "game/mods/42");
        Files.write(new File(mod, ".dd1-workshop").toPath(),
            "42\n1700000000\nMusketeer\n".getBytes(StandardCharsets.UTF_8));

        DD1Workshop.Mod found = DD1Workshop.scan(files).get(0);

        assertEquals(42L, found.publishedFileId);
        assertEquals(1700000000L, found.updatedAt);
        assertEquals("Musketeer", found.title);
        assertEquals("42", found.directoryName);
    }

    @Test
    public void anUnmarkedDirectoryRemainsLocal() throws Exception {
        File files = folder.newFolder();
        mkdir(files, "game/mods/my-local-mod");

        DD1Workshop.Mod found = DD1Workshop.scan(files).get(0);

        assertEquals(0L, found.publishedFileId);
        assertEquals("my-local-mod", found.title);
    }

    @Test
    public void aMalformedMarkerRemainsLocal() throws Exception {
        File files = folder.newFolder();
        File mod = mkdir(files, "game/mods/42");
        Files.write(new File(mod, ".dd1-workshop").toPath(),
            "not-an-id\n7\nWrong\n".getBytes(StandardCharsets.UTF_8));

        assertEquals(0L, DD1Workshop.scan(files).get(0).publishedFileId);
    }

    @Test
    public void scanIsStableByDirectoryName() throws Exception {
        File files = folder.newFolder();
        mkdir(files, "game/mods/z");
        mkdir(files, "game/mods/a");

        List<DD1Workshop.Mod> mods = DD1Workshop.scan(files);

        assertEquals("a", mods.get(0).directoryName);
        assertEquals("z", mods.get(1).directoryName);
    }

    @Test
    public void promotionFindsTheSingleRecognizableChildAndWritesMarker() throws Exception {
        File files = folder.newFolder();
        File payload = mkdir(files, "workshop-staging/42/only-child");
        touch(new File(payload, "project.xml"));

        DD1Workshop.promote(files, 42, 7, "Musketeer");

        File active = new File(files, "game/mods/42");
        assertTrue(new File(active, "project.xml").isFile());
        assertEquals("42\n7\nMusketeer\n", new String(Files.readAllBytes(
            new File(active, ".dd1-workshop").toPath()), StandardCharsets.UTF_8));
        assertFalse(new File(files, "game/mods/42.dd1-backup").exists());
    }

    @Test
    public void payloadWithoutProjectXmlCannotReplaceInstalledCopy() throws Exception {
        File files = folder.newFolder();
        File active = mkdir(files, "game/mods/42");
        touch(new File(active, "old"));
        mkdir(files, "workshop-staging/42");

        expectIOException(() -> DD1Workshop.promote(files, 42, 8, "Bad"));

        assertTrue(new File(active, "old").isFile());
    }

    @Test
    public void payloadContainingASymlinkCannotEscapeStaging() throws Exception {
        File files = folder.newFolder();
        File payload = mkdir(files, "workshop-staging/42");
        touch(new File(payload, "project.xml"));
        File outside = new File(files, "outside");
        touch(outside);
        Files.createSymbolicLink(new File(payload, "escape").toPath(), outside.toPath());

        expectIOException(() -> DD1Workshop.promote(files, 42, 8, "Linked"));

        assertFalse(new File(files, "game/mods/42").exists());
        assertTrue(outside.isFile());
    }

    @Test
    public void deleteRemovesOnlyTheNamedDirectChild() throws Exception {
        File files = folder.newFolder();
        mkdir(files, "game/mods/local");

        DD1Workshop.delete(files, "local");

        assertFalse(new File(files, "game/mods/local").exists());
        expectIOException(() -> DD1Workshop.delete(files, "../game"));
        expectIOException(() -> DD1Workshop.delete(files, new File(files, "game").getAbsolutePath()));
    }

    private static File mkdir(File root, String path) {
        File result = new File(root, path);
        assertTrue(result.mkdirs());
        return result;
    }

    private static void touch(File file) throws IOException {
        Files.write(file.toPath(), new byte[] {1});
    }

    private static void expectIOException(Throwing action) throws Exception {
        try {
            action.run();
            fail("expected IOException");
        }
        catch (IOException expected) {}
    }

    private interface Throwing {
        void run() throws Exception;
    }
}
