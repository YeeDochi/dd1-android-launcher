package com.winlator.dd1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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

        List<DD1SaveSummary.Entry> files = DD1SaveSlots.filesOf(listing, "profile_2", Collections.<String>emptySet());

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

    // Steam named the two files it keeps in the tree's root as though they were
    // inside profile_0, and a slot transfer duly carried them into it - fourteen
    // files became sixteen. What lives where is settled by the local tree: a name
    // that exists at the root is a root file whatever the cloud calls it.
    @Test
    public void aFileTheRootAlreadyHasIsNotPartOfASlot() {
        DD1CloudListing listing = DD1CloudListing.of(1L, Arrays.asList(
            entry("profile_0/persist.game.json"),
            entry("profile_0/persist.options.json"),
            entry("profile_0/steam_init.json")));

        List<DD1SaveSummary.Entry> files = DD1SaveSlots.filesOf(listing, "profile_0",
            new java.util.HashSet<>(Arrays.asList("persist.options.json",
                "steam_init.json")));

        assertEquals(1, files.size());
        assertEquals("profile_0/persist.game.json", files.get(0).path);
    }

    @Test
    public void withNothingKnownAtTheRootEveryNamedFileCounts() {
        DD1CloudListing listing = DD1CloudListing.of(1L, Arrays.asList(
            entry("profile_0/persist.game.json"),
            entry("profile_0/persist.options.json")));

        assertEquals(2, DD1SaveSlots.filesOf(listing, "profile_0",
            Collections.<String>emptySet()).size());
    }

    // Back in the hamlet the game has deleted the expedition's files; an upload
    // has to take them out of the cloud too, or a PC gets the hamlet save with
    // the old dungeon attached. Root files and other slots are never touched.
    @Test
    public void filesTheSlotNoLongerHasAreStaleInTheCloud() {
        DD1CloudListing listing = DD1CloudListing.of(1L, Arrays.asList(
            entry("profile_0/persist.game.json"),
            entry("profile_0/persist.raid.json"),
            entry("profile_0/persist.map.json"),
            entry("profile_0/steam_init.json"),
            entry("profile_1/persist.raid.json")));

        assertEquals(Arrays.asList("profile_0/persist.raid.json", "profile_0/persist.map.json"),
            DD1SaveSlots.staleInCloud(listing, "profile_0",
                Collections.singleton("steam_init.json"),
                Collections.singletonList("profile_0/Persist.Game.json")));
    }

    @Test
    public void anUnknownCloudHasNothingStale() {
        assertTrue(DD1SaveSlots.staleInCloud(DD1CloudListing.unknown(), "profile_0",
            Collections.<String>emptySet(), Collections.<String>emptyList()).isEmpty());
    }

    // Only the game's backup copies may be skipped when Steam lists them with
    // no content; a live file never is.
    @Test
    public void onlyTheBackupFolderCountsAsTheGamesBackup() {
        assertTrue(DD1SaveSlots.isGameBackup("profile_0/backup/persist.roster.json"));
        assertFalse(DD1SaveSlots.isGameBackup("profile_0/persist.roster.json"));
        assertFalse(DD1SaveSlots.isGameBackup("persist.options.json"));
    }

    private static DD1SaveSummary.Entry entry(String path) {
        return new DD1SaveSummary.Entry(path, 10, 0L, "aaa");
    }
}
