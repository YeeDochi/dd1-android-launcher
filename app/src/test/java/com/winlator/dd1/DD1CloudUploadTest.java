package com.winlator.dd1;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class DD1CloudUploadTest {
    // Steam defines zero as "download nowhere", not "use the default". That
    // leaves API readers able to see the file while every desktop rejects it.
    @Test
    public void uploadsTargetEverySteamPlatform() {
        assertEquals(-1, DD1CloudSaves.PLATFORMS_TO_SYNC);
    }

    // Uploading nothing is how a cloud gets emptied. The funnel refuses it here
    // so no caller has to remember to.
    @Test
    public void anEmptySetIsNeverUploaded() {
        assertFalse(DD1CloudSaves.uploadable(Collections.emptyList()));
    }

    // A zero-length save is what an interrupted write leaves behind, and this
    // project has already shipped an install made entirely of those once.
    @Test
    public void aZeroLengthSaveIsNeverUploaded() {
        assertFalse(DD1CloudSaves.uploadable(Arrays.asList(
            new DD1SaveSummary.Entry("profile_0/persist.game.json", 0, 0L, "aaa"))));
    }

    @Test
    public void aPathOutOfTheTreeIsNeverUploaded() {
        assertFalse(DD1CloudSaves.uploadable(Arrays.asList(
            new DD1SaveSummary.Entry("../escape.json", 10, 0L, "aaa"))));
    }

    // One bad file in the set stops the set: a batch that uploads some of a save
    // is a save nobody can load.
    @Test
    public void oneUnacceptableFileStopsTheWholeSet() {
        assertFalse(DD1CloudSaves.uploadable(Arrays.asList(
            new DD1SaveSummary.Entry("profile_0/persist.game.json", 2140, 0L, "aaa"),
            new DD1SaveSummary.Entry("profile_0/persist.town.json", 0, 0L, "bbb"))));
    }

    @Test
    public void realSavesAreUploadable() {
        assertTrue(DD1CloudSaves.uploadable(Arrays.asList(
            new DD1SaveSummary.Entry("profile_0/persist.game.json", 2140, 0L, "aaa"),
            new DD1SaveSummary.Entry("profile_0/persist.town.json", 918, 0L, "bbb"))));
    }
}
