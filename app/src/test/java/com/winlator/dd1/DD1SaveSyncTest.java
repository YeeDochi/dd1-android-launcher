package com.winlator.dd1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class DD1SaveSyncTest {
    private static DD1SaveSummary.Entry file(String path, String sha, long time) {
        return new DD1SaveSummary.Entry(path, 10, time, sha);
    }

    private static final List<DD1SaveSummary.Entry> SAVE = Arrays.asList(
        file("profile_0/persist.game.json", "aa", 1000), file("profile_0/persist.town.json", "bb", 2000));

    @Test
    public void theSameFilesAreTheSame() {
        assertEquals(DD1SaveSync.State.SAME, DD1SaveSync.of(SAVE, Arrays.asList(
            file("profile_0/Persist.Game.json", "AA", 5), file("profile_0/persist.town.json", "bb", 6))));
    }

    @Test
    public void oneFileOffOrOneFileMoreIsDifferent() {
        assertEquals(DD1SaveSync.State.DIFFERENT, DD1SaveSync.of(SAVE, Arrays.asList(
            file("profile_0/persist.game.json", "aa", 0), file("profile_0/persist.town.json", "cc", 0))));
        assertEquals(DD1SaveSync.State.DIFFERENT, DD1SaveSync.of(SAVE, Arrays.asList(
            file("profile_0/persist.game.json", "aa", 0), file("profile_0/persist.town.json", "bb", 0),
            file("profile_0/persist.raid.json", "dd", 0))));
    }

    @Test
    public void oneSideOnly() {
        assertEquals(DD1SaveSync.State.LOCAL_ONLY,
            DD1SaveSync.of(SAVE, Collections.<DD1SaveSummary.Entry>emptyList()));
        assertEquals(DD1SaveSync.State.CLOUD_ONLY,
            DD1SaveSync.of(Collections.<DD1SaveSummary.Entry>emptyList(), SAVE));
    }

    // profile_9 in the cloud holds nothing but the tutorial record.
    @Test
    public void aTutorialRecordAloneIsNoSave() {
        assertFalse(DD1SaveSync.hasGame(Collections.singletonList(
            file("profile_9/persist.tutorial.json", "ee", 0))));
    }

    @Test
    public void theNewestFileSaysWhenItWasSaved() {
        assertEquals(2000, DD1SaveSync.newest(SAVE));
    }
}
