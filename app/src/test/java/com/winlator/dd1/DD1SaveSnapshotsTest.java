package com.winlator.dd1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

public class DD1SaveSnapshotsTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void aSnapshotCopiesTheSaveWithoutTouchingIt() throws IOException {
        File files = folder.newFolder();
        save(files, "abc");

        File snapshot = DD1SaveSnapshots.take(files, 1000L);

        assertEquals("1000", snapshot.getName());
        assertTrue(new File(snapshot, "profile_0/persist.game.json").isFile());
        assertTrue(new File(DD1Saves.root(files), "profile_0/persist.game.json").isFile());
    }

    @Test
    public void thereIsNothingToSnapshotBeforeTheGameHasSaved() throws IOException {
        assertNull(DD1SaveSnapshots.take(folder.newFolder(), 1000L));
    }

    // Three is the whole point of the ring: the fourth pushes the first out
    // rather than filling the phone.
    @Test
    public void onlyTheLatestThreeAreKept() throws IOException {
        File files = folder.newFolder();
        save(files, "abc");

        DD1SaveSnapshots.take(files, 1000L);
        DD1SaveSnapshots.take(files, 2000L);
        DD1SaveSnapshots.take(files, 3000L);
        DD1SaveSnapshots.take(files, 4000L);

        List<File> kept = DD1SaveSnapshots.kept(files);
        assertEquals(3, kept.size());
        assertEquals("4000", kept.get(0).getName());
        assertEquals("3000", kept.get(1).getName());
        assertEquals("2000", kept.get(2).getName());
    }

    @Test
    public void takingTheSameMomentTwiceReplacesRatherThanDuplicates() throws IOException {
        File files = folder.newFolder();
        save(files, "first");
        DD1SaveSnapshots.take(files, 1000L);
        save(files, "second");

        File snapshot = DD1SaveSnapshots.take(files, 1000L);

        assertEquals(1, DD1SaveSnapshots.kept(files).size());
        assertEquals(6, new File(snapshot, "profile_0/persist.game.json").length());
    }

    // A snapshot nobody can go back to is decoration. Restoring one is the other
    // half of the promise, and it snapshots the state it is about to replace so
    // that going back is itself reversible.
    @Test
    public void restoringPutsASnapshotBackAndKeepsWhatItReplaced() throws IOException {
        File files = folder.newFolder();
        save(files, "an early campaign");
        File early = DD1SaveSnapshots.take(files, 1000L);
        save(files, "much later");

        assertTrue(DD1SaveSnapshots.restore(files, early));

        assertEquals("an early campaign".length(),
            new File(DD1Saves.root(files), "profile_0/persist.game.json").length());
        assertEquals(2, DD1SaveSnapshots.kept(files).size());
    }

    // With the ring full, restoring the oldest snapshot takes a fourth first,
    // and the prune that follows deleted the very snapshot being restored.
    @Test
    public void theOldestSnapshotOfAFullRingCanBeRestored() throws IOException {
        File files = folder.newFolder();
        save(files, "the save that was deleted");
        File oldest = DD1SaveSnapshots.take(files, 1000L);
        save(files, "second");
        DD1SaveSnapshots.take(files, 2000L);
        save(files, "third");
        DD1SaveSnapshots.take(files, 3000L);
        save(files, "what is there now");

        assertTrue(DD1SaveSnapshots.restore(files, oldest));

        assertEquals("the save that was deleted".length(),
            new File(DD1Saves.root(files), "profile_0/persist.game.json").length());
        assertEquals(DD1SaveSnapshots.KEPT, DD1SaveSnapshots.kept(files).size());
    }

    @Test
    public void restoringSomethingThatIsNotASnapshotIsRefused() throws IOException {
        File files = folder.newFolder();
        save(files, "the only campaign");

        assertFalse(DD1SaveSnapshots.restore(files, new File(files, "nowhere")));

        assertEquals("the only campaign".length(),
            new File(DD1Saves.root(files), "profile_0/persist.game.json").length());
    }

    // The keepsake copy goes wherever it is pointed - in the app it is a public
    // folder a file manager can open - and carries the whole tree unrenamed, so
    // putting a file back is a plain copy.
    @Test
    public void exportingCopiesTheWholeTreeToTheGivenFolder() throws IOException {
        File files = folder.newFolder();
        save(files, "abc");
        File destination = new File(folder.newFolder(), "2026-08-28_10-00-00");

        assertTrue(DD1SaveSnapshots.export(files, destination));

        assertEquals(3, new File(destination, "profile_0/persist.game.json").length());
        assertTrue(new File(DD1Saves.root(files), "profile_0/persist.game.json").isFile());
    }

    @Test
    public void thereIsNothingToExportBeforeTheGameHasSaved() throws IOException {
        File destination = new File(folder.newFolder(), "backup");
        assertFalse(DD1SaveSnapshots.export(folder.newFolder(), destination));
        assertFalse(destination.exists());
    }

    // The mirror follows the live tree, not the ring: the ring's newest entry is
    // always a play session old, and the mirror exists for the moment the app -
    // and the ring inside it - is deleted.
    @Test
    public void theMirrorFollowsTheLiveTreeAndSkipsWhatHasNotMoved() throws IOException {
        File files = folder.newFolder();
        File mirror = new File(folder.newFolder(), "auto");
        save(files, "first");

        DD1SaveSnapshots.mirror(files, mirror, hours(1));
        DD1SaveSnapshots.mirror(files, mirror, hours(2));

        assertEquals(1, DD1SaveSnapshots.mirrored(mirror).size());

        save(files, "second");
        DD1SaveSnapshots.mirror(files, mirror, hours(3));

        List<File> copies = DD1SaveSnapshots.mirrored(mirror);
        assertEquals(2, copies.size());
        assertEquals("second".length(),
            new File(copies.get(0), "profile_0/persist.game.json").length());
    }

    @Test
    public void onlyTheNewestThreeMirrorsAreKept() throws IOException {
        File files = folder.newFolder();
        File mirror = new File(folder.newFolder(), "auto");
        for (int i = 1; i <= 4; i++) {
            save(files, "state " + i);
            DD1SaveSnapshots.mirror(files, mirror, hours(i));
        }

        List<File> copies = DD1SaveSnapshots.mirrored(mirror);
        assertEquals(3, copies.size());
        assertEquals("state 4".length(),
            new File(copies.get(0), "profile_0/persist.game.json").length());
    }

    @Test
    public void thereIsNothingToMirrorBeforeTheGameHasSaved() throws IOException {
        File mirror = new File(folder.newFolder(), "auto");
        DD1SaveSnapshots.mirror(folder.newFolder(), mirror, hours(1));
        assertTrue(DD1SaveSnapshots.mirrored(mirror).isEmpty());
    }

    private static long hours(int count) {
        return count * 3_600_000L;
    }

    private static void save(File files, String content) throws IOException {
        File dir = new File(DD1Saves.root(files), "profile_0");
        dir.mkdirs();
        try (OutputStream out = new FileOutputStream(new File(dir, "persist.game.json"))) {
            out.write(content.getBytes("UTF-8"));
        }
    }
}
