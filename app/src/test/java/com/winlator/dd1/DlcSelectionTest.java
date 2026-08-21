package com.winlator.dd1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class DlcSelectionTest {
    private static final List<Integer> OWNED = Arrays.asList(580100, 702540, 735730);
    // What Steam's depot table for Darkest Dungeon actually lists.
    private static final List<Integer> DLC =
        Arrays.asList(445700, 580100, 702540, 735730, 1117860, 4964110);

    @Test
    public void everythingOwnedIsSelectedTheFirstTime() {
        DlcSelection selection = DlcSelection.parse(null, OWNED, DLC);

        assertEquals(OWNED, selection.selected());
        assertTrue(selection.isSelected(580100));
    }

    @Test
    public void aStoredChoiceSurvivesARoundTrip() {
        DlcSelection selection = DlcSelection.parse(null, OWNED, DLC);
        selection.setSelected(702540, false);

        DlcSelection reloaded = DlcSelection.parse(selection.serialize(), OWNED, DLC);

        assertFalse(reloaded.isSelected(702540));
        assertEquals(Arrays.asList(580100, 735730), reloaded.selected());
    }

    @Test
    public void newlyBoughtContentIsSelectedWithoutTouchingOldChoices() {
        DlcSelection selection = DlcSelection.parse(null, OWNED, DLC);
        selection.setSelected(580100, false);

        List<Integer> grown = Arrays.asList(580100, 702540, 735730, 4964110);
        DlcSelection reloaded = DlcSelection.parse(selection.serialize(), grown, DLC);

        assertFalse(reloaded.isSelected(580100));
        assertTrue("a DLC bought later is on by default", reloaded.isSelected(4964110));
    }

    @Test
    public void contentTheAccountNoLongerOwnsDisappears() {
        DlcSelection selection = DlcSelection.parse(null, OWNED, DLC);
        DlcSelection reloaded = DlcSelection.parse(selection.serialize(), Arrays.asList(580100), DLC);

        assertEquals(Arrays.asList(580100), reloaded.selected());
    }

    @Test
    public void onlyThisGamesContentIsOffered() {
        DlcSelection selection = DlcSelection.parse(null,
            Arrays.asList(DD1SteamEvents.APP_ID, 580100, 440), DLC);

        assertEquals(Arrays.asList(580100), selection.selected());
    }

    @Test
    public void deselectingEverythingIsAllowed() {
        DlcSelection selection = DlcSelection.parse(null, OWNED, DLC);
        for (int id : OWNED) selection.setSelected(id, false);

        assertEquals(Collections.emptyList(), selection.selected());
    }

    // The Musketeer is owned by everyone and was in no list anybody typed out, so
    // it was never offered, never downloaded, and the game said it was missing.
    @Test
    public void contentWithNoNameOfItsOwnIsStillOffered() {
        DlcSelection selection = DlcSelection.parse(null,
            Arrays.asList(445700, 580100, 999999), DLC);

        assertTrue(selection.selected().contains(445700));
        assertFalse("999999 is not this game's DLC", selection.selected().contains(999999));
    }

    @Test
    public void aDlcSteamAddsLaterNeedsNoCodeChange() {
        List<Integer> grownTable = Arrays.asList(445700, 580100, 5000001);
        DlcSelection selection = DlcSelection.parse(null,
            Arrays.asList(445700, 580100, 5000001), grownTable);

        assertEquals(grownTable, selection.selected());
        assertEquals("DLC 5000001", DlcSelection.nameOf(5000001));
    }

    @Test
    public void knownContentIsNamedAndTheRestKeepsItsNumber() {
        assertEquals("The Crimson Court", DlcSelection.nameOf(580100));
        assertEquals("The Color of Madness", DlcSelection.nameOf(735730));
        assertEquals("The Musketeer", DlcSelection.nameOf(445700));
        assertEquals("DLC 999999", DlcSelection.nameOf(999999));
    }
}
