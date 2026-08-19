package com.winlator.dd1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ModSyncPlanTest {
    private static final long OLD = 1_700_000_000L;
    private static final long NEW = 1_800_000_000L;

    @Test
    public void subscribedButMissingIsInstalled() {
        ModSyncPlan plan = ModSyncPlan.of(
            Arrays.asList(subscribed(1, "Musketeer", NEW)), Collections.emptyList());

        assertEquals(Arrays.asList(1L), ids(plan.install));
        assertTrue(plan.update.isEmpty());
    }

    @Test
    public void newerSubscriptionIsAnUpdate() {
        ModSyncPlan plan = ModSyncPlan.of(
            Arrays.asList(subscribed(1, "Musketeer", NEW)),
            Arrays.asList(installed(1, OLD, true)));

        assertEquals(Arrays.asList(1L), ids(plan.update));
        assertTrue(plan.install.isEmpty());
    }

    @Test
    public void updatesForDisabledModsAreListedApart() {
        ModSyncPlan plan = ModSyncPlan.of(
            Arrays.asList(subscribed(1, "Musketeer", NEW)),
            Arrays.asList(installed(1, OLD, false)));

        assertEquals(Arrays.asList(1L), ids(plan.disabledUpdate));
        assertTrue(plan.update.isEmpty());
    }

    @Test
    public void sameTimestampIsLeftAlone() {
        ModSyncPlan plan = ModSyncPlan.of(
            Arrays.asList(subscribed(1, "Musketeer", NEW)),
            Arrays.asList(installed(1, NEW, true)));

        assertTrue(plan.hasWork());
        assertEquals(Collections.emptyList(), ids(plan.update));
        assertEquals(Collections.emptyList(), ids(plan.install));
        assertTrue(plan.upToDate.contains(1L));
    }

    @Test
    public void unsubscribedItemBecomesAnOrphanAndIsNeverDeleted() {
        ModSyncPlan plan = ModSyncPlan.of(
            Collections.emptyList(), Arrays.asList(installed(7, OLD, true)));

        assertEquals(1, plan.orphan.size());
        assertEquals(7L, plan.orphan.get(0).publishedFileId);
        assertTrue(plan.install.isEmpty());
        assertTrue(plan.update.isEmpty());
    }

    @Test
    public void itemWithoutContentIsSkippedWithAReason() {
        ModSyncPlan plan = ModSyncPlan.of(
            Arrays.asList(new ModSyncPlan.Subscribed(2, "Broken", NEW, false)),
            Collections.emptyList());

        assertEquals(1, plan.skipped.size());
        assertEquals(2L, plan.skipped.get(0).publishedFileId);
        assertEquals("no downloadable content", plan.skipped.get(0).reason);
        assertTrue(plan.install.isEmpty());
    }

    @Test
    public void nothingSubscribedAndNothingInstalledIsNoWork() {
        assertTrue(!ModSyncPlan.of(Collections.emptyList(), Collections.emptyList()).hasWork());
    }

    private static ModSyncPlan.Subscribed subscribed(long id, String title, long updated) {
        return new ModSyncPlan.Subscribed(id, title, updated, true);
    }

    private static ModSyncPlan.Installed installed(long id, long updated, boolean enabled) {
        return new ModSyncPlan.Installed(id, updated, enabled);
    }

    private static List<Long> ids(List<ModSyncPlan.Subscribed> items) {
        List<Long> result = new java.util.ArrayList<>();
        for (ModSyncPlan.Subscribed item : items) result.add(item.publishedFileId);
        return result;
    }
}
