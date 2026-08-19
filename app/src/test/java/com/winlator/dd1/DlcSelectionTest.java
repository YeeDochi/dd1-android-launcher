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

    @Test
    public void everythingOwnedIsSelectedTheFirstTime() {
        DlcSelection selection = DlcSelection.parse(null, OWNED);

        assertEquals(OWNED, selection.selected());
        assertTrue(selection.isSelected(580100));
    }

    @Test
    public void aStoredChoiceSurvivesARoundTrip() {
        DlcSelection selection = DlcSelection.parse(null, OWNED);
        selection.setSelected(702540, false);

        DlcSelection reloaded = DlcSelection.parse(selection.serialize(), OWNED);

        assertFalse(reloaded.isSelected(702540));
        assertEquals(Arrays.asList(580100, 735730), reloaded.selected());
    }

    @Test
    public void newlyBoughtContentIsSelectedWithoutTouchingOldChoices() {
        DlcSelection selection = DlcSelection.parse(null, OWNED);
        selection.setSelected(580100, false);

        List<Integer> grown = Arrays.asList(580100, 702540, 735730, 4964110);
        DlcSelection reloaded = DlcSelection.parse(selection.serialize(), grown);

        assertFalse(reloaded.isSelected(580100));
        assertTrue("a DLC bought later is on by default", reloaded.isSelected(4964110));
    }

    @Test
    public void contentTheAccountNoLongerOwnsDisappears() {
        DlcSelection selection = DlcSelection.parse(null, OWNED);
        DlcSelection reloaded = DlcSelection.parse(selection.serialize(), Arrays.asList(580100));

        assertEquals(Arrays.asList(580100), reloaded.selected());
    }

    @Test
    public void theBaseGameIsNeverASelectableExtra() {
        DlcSelection selection = DlcSelection.parse(null,
            Arrays.asList(DD1SteamEvents.APP_ID, 580100));

        assertEquals(Arrays.asList(580100), selection.selected());
    }

    @Test
    public void deselectingEverythingIsAllowed() {
        DlcSelection selection = DlcSelection.parse(null, OWNED);
        for (int id : OWNED) selection.setSelected(id, false);

        assertEquals(Collections.emptyList(), selection.selected());
    }

    @Test
    public void knownContentIsNamedAndTheRestKeepsItsNumber() {
        assertEquals("The Crimson Court", DlcSelection.nameOf(580100));
        assertEquals("The Color of Madness", DlcSelection.nameOf(735730));
        assertEquals("DLC 999999", DlcSelection.nameOf(999999));
    }
}
