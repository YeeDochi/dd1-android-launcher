package com.winlator.dd1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class DD1CloudListingTest {
    // A listing that could not be read is the dangerous case: read as empty, it
    // invites an upload that deletes a PC's progress.
    @Test
    public void anUnknownListingIsNotAnEmptyOne() {
        DD1CloudListing unknown = DD1CloudListing.unknown();

        assertFalse(unknown.known());
        assertEquals(0, unknown.files().size());
    }

    @Test
    public void anEmptyCloudIsKnownToBeEmpty() {
        DD1CloudListing listing = DD1CloudListing.of(1L, Collections.emptyList());

        assertTrue(listing.known());
        assertEquals(1L, listing.changeNumber());
        assertEquals(0, listing.files().size());
    }

    @Test
    public void steamsFieldsBecomeTheSameShapeAsALocalSummary() {
        byte[] sha1 = new byte[] {1, 2, 3};

        DD1SaveSummary.Entry entry =
            DD1CloudListing.entry("profile_0/persist.game.json", 2140, sha1, 5000L);

        assertEquals("profile_0/persist.game.json", entry.path);
        assertEquals(2140, entry.length);
        assertEquals(5000L, entry.modifiedMillis);
        assertEquals("010203", entry.sha1);
    }

    @Test
    public void theListingKeepsWhatItWasGiven() {
        DD1CloudListing listing = DD1CloudListing.of(7L, Arrays.asList(
            DD1CloudListing.entry("steam_init.json", 166, new byte[] {(byte)0xff}, 1L)));

        assertEquals(1, listing.files().size());
        assertEquals("ff", listing.files().get(0).sha1);
    }
}
