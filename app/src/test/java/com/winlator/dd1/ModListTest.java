package com.winlator.dd1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ModListTest {
    @Test
    public void keepsTheStoredOrderAndEnabledState() {
        ModList list = ModList.parse("+a\n-b\n");
        list.reconcile(Arrays.asList("b", "a"));

        assertEquals(Arrays.asList("a", "b"), list.ids());
        assertTrue(list.isEnabled("a"));
        assertFalse(list.isEnabled("b"));
    }

    @Test
    public void appendsModsAddedOutsideTheLauncherAsEnabled() {
        ModList list = ModList.parse("+a\n");
        list.reconcile(Arrays.asList("a", "c"));

        assertEquals(Arrays.asList("a", "c"), list.ids());
        assertTrue(list.isEnabled("c"));
    }

    @Test
    public void dropsRowsWhoseFolderIsGone() {
        ModList list = ModList.parse("+a\n+gone\n");
        list.reconcile(Collections.singletonList("a"));

        assertEquals(Arrays.asList("a"), list.ids());
    }

    @Test
    public void movesEntriesWithoutFallingOffTheEnds() {
        ModList list = ModList.parse("");
        list.reconcile(Arrays.asList("a", "b", "c"));

        list.move("c", -1);
        assertEquals(Arrays.asList("a", "c", "b"), list.ids());

        list.move("a", -1);
        assertEquals(Arrays.asList("a", "c", "b"), list.ids());

        list.move("b", 1);
        assertEquals(Arrays.asList("a", "c", "b"), list.ids());
    }

    @Test
    public void writesOnlyEnabledModsInOrder() {
        ModList list = ModList.parse("");
        list.reconcile(Arrays.asList("a", "b", "c"));
        list.setEnabled("b", false);

        assertEquals(Arrays.asList("a", "c"), list.loadOrder());
    }

    @Test
    public void survivesARoundTripThroughStorage() {
        ModList list = ModList.parse("");
        list.reconcile(Arrays.asList("a", "b"));
        list.setEnabled("a", false);

        ModList reloaded = ModList.parse(list.serialize());
        reloaded.reconcile(Arrays.asList("b", "a"));

        assertEquals(Arrays.asList("a", "b"), reloaded.ids());
        assertFalse(reloaded.isEnabled("a"));
    }

    @Test
    public void unreadableStorageStartsEmptyInsteadOfThrowing() {
        List<String> scanned = Arrays.asList("a");
        ModList list = ModList.parse("garbage");
        list.reconcile(scanned);

        assertEquals(scanned, list.ids());
    }
}
