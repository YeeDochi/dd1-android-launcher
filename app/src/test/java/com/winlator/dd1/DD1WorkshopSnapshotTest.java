package com.winlator.dd1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class DD1WorkshopSnapshotTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void readySnapshotNamesInstallUpdateOrphanAndLocalRows() throws Exception {
        File files = folder.newFolder();
        workshop(files, 2, 1, "Old");
        workshop(files, 3, 1, "Orphan");
        new File(files, "game/mods/local").mkdirs();
        List<ModSyncPlan.Subscribed> subscriptions = Arrays.asList(
            new ModSyncPlan.Subscribed(1, "New", 4, true),
            new ModSyncPlan.Subscribed(2, "Updated", 5, true));

        DD1WorkshopSnapshot snapshot = DD1WorkshopSnapshot.ready(subscriptions,
            DD1Workshop.scan(files));

        assertEquals(DD1WorkshopSnapshot.State.INSTALL, snapshot.find(1).state);
        assertEquals(DD1WorkshopSnapshot.State.UPDATE, snapshot.find(2).state);
        assertEquals(DD1WorkshopSnapshot.State.ORPHAN, snapshot.find(3).state);
        assertEquals(DD1WorkshopSnapshot.State.LOCAL, snapshot.findDirectory("local").state);
        assertTrue(snapshot.syncable());
        assertEquals(Arrays.asList(1L, 2L), ids(snapshot.syncItems()));
    }

    @Test
    public void currentItemsNeedNoSync() throws Exception {
        File files = folder.newFolder();
        workshop(files, 2, 5, "Current");

        DD1WorkshopSnapshot snapshot = DD1WorkshopSnapshot.ready(Collections.singletonList(
            new ModSyncPlan.Subscribed(2, "Current", 5, true)), DD1Workshop.scan(files));

        assertEquals(DD1WorkshopSnapshot.State.CURRENT, snapshot.find(2).state);
        assertFalse(snapshot.syncable());
    }

    @Test
    public void unusableSubscriptionIsShownAsSkipped() {
        DD1WorkshopSnapshot snapshot = DD1WorkshopSnapshot.ready(Collections.singletonList(
            new ModSyncPlan.Subscribed(9, "Broken", 5, false)), Collections.emptyList());

        assertEquals(DD1WorkshopSnapshot.State.SKIPPED, snapshot.find(9).state);
        assertFalse(snapshot.syncable());
    }

    private static void workshop(File files, long id, long updated, String title) throws Exception {
        File mod = new File(files, "game/mods/" + id);
        mod.mkdirs();
        Files.write(new File(mod, ".dd1-workshop").toPath(),
            (id + "\n" + updated + "\n" + title + "\n").getBytes(StandardCharsets.UTF_8));
    }

    private static List<Long> ids(List<ModSyncPlan.Subscribed> items) {
        java.util.ArrayList<Long> ids = new java.util.ArrayList<>();
        for (ModSyncPlan.Subscribed item : items) ids.add(item.publishedFileId);
        return ids;
    }
}
