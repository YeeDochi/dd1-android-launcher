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
import java.util.Arrays;
import java.util.List;

public class DD1SaveSummaryTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void describesEveryFileByPathLengthAndDigest() throws IOException {
        File root = folder.newFolder("Darkest");
        write(root, "profile_0/persist.game.json", "abc");

        List<DD1SaveSummary.Entry> entries = DD1SaveSummary.of(root);

        assertEquals(1, entries.size());
        assertEquals("profile_0/persist.game.json", entries.get(0).path);
        assertEquals(3, entries.get(0).length);
        assertEquals("a9993e364706816aba3e25717850c26c9cd0d89d", entries.get(0).sha1);
    }

    // The same bytes written twice must read as unchanged, or every launch would
    // look like a save worth uploading.
    @Test
    public void identicalContentIsNotAChange() throws IOException {
        File root = folder.newFolder("Darkest");
        write(root, "profile_0/persist.game.json", "abc");
        List<DD1SaveSummary.Entry> before = DD1SaveSummary.of(root);
        write(root, "profile_0/persist.game.json", "abc");

        assertEquals(0, DD1SaveSummary.changed(before, DD1SaveSummary.of(root)).size());
    }

    @Test
    public void editedAddedAndRemovedFilesAreAllChanges() throws IOException {
        File root = folder.newFolder("Darkest");
        write(root, "profile_0/persist.game.json", "abc");
        write(root, "profile_0/persist.town.json", "keep");
        List<DD1SaveSummary.Entry> before = DD1SaveSummary.of(root);

        write(root, "profile_0/persist.game.json", "different");
        write(root, "profile_0/persist.roster.json", "new");
        new File(root, "profile_0/persist.town.json").delete();

        List<String> changed = DD1SaveSummary.changed(before, DD1SaveSummary.of(root));

        assertEquals(Arrays.asList(
            "profile_0/persist.game.json",
            "profile_0/persist.roster.json",
            "profile_0/persist.town.json"), changed);
    }

    @Test
    public void pathsAreRelativeAndUseForwardSlashes() throws IOException {
        File root = folder.newFolder("Darkest");
        write(root, "profile_0/persist.game.json", "abc");

        String path = DD1SaveSummary.of(root).get(0).path;

        assertFalse(path.startsWith("/"));
        assertFalse(path.contains("\\"));
    }

    @Test
    public void aPathThatClimbsOutOfTheTreeIsRefused() {
        assertFalse(DD1SaveSummary.acceptable(
            new DD1SaveSummary.Entry("../persist.game.json", 3, 0, "x")));
        assertFalse(DD1SaveSummary.acceptable(
            new DD1SaveSummary.Entry("/etc/passwd", 3, 0, "x")));
        assertTrue(DD1SaveSummary.acceptable(
            new DD1SaveSummary.Entry("profile_0/persist.game.json", 3, 0, "x")));
    }

    // Steam refuses anything larger, so a file that big is not a save Steam will
    // ever hold and sending it would fail the whole batch.
    @Test
    public void aFileOverAHundredMebibytesIsRefused() {
        assertFalse(DD1SaveSummary.acceptable(
            new DD1SaveSummary.Entry("profile_0/big.json", 100L * 1024 * 1024 + 1, 0, "x")));
        assertTrue(DD1SaveSummary.acceptable(
            new DD1SaveSummary.Entry("profile_0/big.json", 100L * 1024 * 1024, 0, "x")));
    }

    @Test
    public void aMissingTreeSummarisesToNothing() throws IOException {
        assertEquals(0, DD1SaveSummary.of(new File(folder.newFolder(), "absent")).size());
    }

    private static void write(File root, String path, String text) throws IOException {
        File file = new File(root, path);
        file.getParentFile().mkdirs();
        try (OutputStream out = new FileOutputStream(file)) {
            out.write(text.getBytes("UTF-8"));
        }
    }
}
