package com.winlator.dd1;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class DD1CloudPlanTest {
    private static final List<DD1SaveSummary.Entry> NONE = Collections.emptyList();

    @Test
    public void anUnreadableCloudMeansLocalOnly() {
        DD1CloudPlan plan = DD1CloudPlan.between(
            Arrays.asList(entry("profile_0/persist.game.json", "aaa")),
            DD1CloudListing.unknown(), NONE);

        assertEquals(DD1CloudPlan.Action.LOCAL_ONLY, plan.action());
        assertEquals(0, plan.paths().size());
    }

    @Test
    public void matchingSidesNeedNothing() {
        List<DD1SaveSummary.Entry> same =
            Arrays.asList(entry("profile_0/persist.game.json", "aaa"));

        assertEquals(DD1CloudPlan.Action.NOTHING,
            DD1CloudPlan.between(same, DD1CloudListing.of(1L, same), same).action());
    }

    // Only the phone moved since the last sync, so the phone is right.
    @Test
    public void localAloneMovingIsAnUpload() {
        List<DD1SaveSummary.Entry> synced =
            Arrays.asList(entry("profile_0/persist.game.json", "aaa"));
        List<DD1SaveSummary.Entry> local =
            Arrays.asList(entry("profile_0/persist.game.json", "bbb"));

        DD1CloudPlan plan = DD1CloudPlan.between(local, DD1CloudListing.of(1L, synced), synced);

        assertEquals(DD1CloudPlan.Action.UPLOAD, plan.action());
        assertEquals(Arrays.asList("profile_0/persist.game.json"), plan.paths());
    }

    @Test
    public void theCloudAloneMovingIsADownload() {
        List<DD1SaveSummary.Entry> synced =
            Arrays.asList(entry("profile_0/persist.game.json", "aaa"));
        List<DD1SaveSummary.Entry> cloud =
            Arrays.asList(entry("profile_0/persist.game.json", "ccc"));

        DD1CloudPlan plan = DD1CloudPlan.between(synced, DD1CloudListing.of(2L, cloud), synced);

        assertEquals(DD1CloudPlan.Action.DOWNLOAD, plan.action());
        assertEquals(Arrays.asList("profile_0/persist.game.json"), plan.paths());
    }

    // Both moved. Nothing here picks a winner; the player does.
    @Test
    public void bothMovingIsAConflictAndStaysOne() {
        List<DD1SaveSummary.Entry> synced =
            Arrays.asList(entry("profile_0/persist.game.json", "aaa"));
        List<DD1SaveSummary.Entry> local =
            Arrays.asList(entry("profile_0/persist.game.json", "bbb"));
        List<DD1SaveSummary.Entry> cloud =
            Arrays.asList(entry("profile_0/persist.game.json", "ccc"));

        DD1CloudPlan plan = DD1CloudPlan.between(local, DD1CloudListing.of(2L, cloud), synced);

        assertEquals(DD1CloudPlan.Action.CONFLICT, plan.action());
        assertEquals(Arrays.asList("profile_0/persist.game.json"), plan.paths());
    }

    // A first run has no record of a sync, and a save on each side that differs
    // is not something to resolve by guessing which came first.
    @Test
    public void noRecordOfASyncWithBothSidesFullIsAConflict() {
        DD1CloudPlan plan = DD1CloudPlan.between(
            Arrays.asList(entry("profile_0/persist.game.json", "bbb")),
            DD1CloudListing.of(1L, Arrays.asList(entry("profile_0/persist.game.json", "ccc"))),
            NONE);

        assertEquals(DD1CloudPlan.Action.CONFLICT, plan.action());
    }

    @Test
    public void aFirstUploadFromAnEmptyCloudIsNotAConflict() {
        DD1CloudPlan plan = DD1CloudPlan.between(
            Arrays.asList(entry("profile_0/persist.game.json", "bbb")),
            DD1CloudListing.of(1L, NONE), NONE);

        assertEquals(DD1CloudPlan.Action.UPLOAD, plan.action());
    }

    private static DD1SaveSummary.Entry entry(String path, String sha1) {
        return new DD1SaveSummary.Entry(path, 10, 0L, sha1);
    }
}
