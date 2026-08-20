package com.winlator.dd1;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class DD1SaveSlotsTest {
    // Ten sorts after two, and the two files Steam keeps in the root belong to no
    // slot at all - which is what keeps them out of every transfer.
    @Test
    public void cloudSlotsComeFromThePathsAndSortByNumber() {
        DD1CloudListing listing = DD1CloudListing.of(1L, Arrays.asList(
            entry("profile_10/persist.game.json"),
            entry("profile_2/persist.game.json"),
            entry("profile_2/persist.town.json"),
            entry("persist.options.json")));

        assertEquals(Arrays.asList("profile_2", "profile_10"),
            DD1SaveSlots.cloudSlotNames(listing));
    }

    @Test
    public void aSlotsFilesAreTheOnesUnderIt() {
        DD1CloudListing listing = DD1CloudListing.of(1L, Arrays.asList(
            entry("profile_2/persist.game.json"),
            entry("profile_2/persist.town.json"),
            entry("profile_3/persist.game.json")));

        List<DD1SaveSummary.Entry> files = DD1SaveSlots.filesOf(listing, "profile_2");

        assertEquals(2, files.size());
        assertEquals("profile_2/persist.game.json", files.get(0).path);
    }

    // A cloud nobody could read has no slots, and it must not look like a cloud
    // with none.
    @Test
    public void anUnknownCloudListsNothing() {
        assertEquals(Collections.emptyList(),
            DD1SaveSlots.cloudSlotNames(DD1CloudListing.unknown()));
    }

    private static DD1SaveSummary.Entry entry(String path) {
        return new DD1SaveSummary.Entry(path, 10, 0L, "aaa");
    }
}
