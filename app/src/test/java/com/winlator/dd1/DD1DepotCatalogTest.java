package com.winlator.dd1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.Arrays;

public class DD1DepotCatalogTest {
    // Every DLC ships three depots, one per platform, and the order is not
    // fixed: The Fire's Edge is windows, linux, macos where the others are
    // windows, macos, linux. Picking by offset from the app id fetches macOS.
    @Test
    public void picksTheWindowsDepotWhateverTheOrder() {
        DD1DepotCatalog catalog = DD1DepotCatalog.of(Arrays.asList(
            new DD1DepotCatalog.Row(4964110, 4964110, "windows", "111"),
            new DD1DepotCatalog.Row(4964111, 4964110, "linux", "222"),
            new DD1DepotCatalog.Row(4964112, 4964110, "macos", "333")));

        assertEquals(4964110, catalog.depotOf(4964110));
        assertEquals("111", catalog.manifestOf(4964110));
    }

    // The base game's depots carry no dlcappid, and reading them as a DLC would
    // offer the whole game as an add-on.
    @Test
    public void ignoresDepotsThatBelongToNoDlc() {
        DD1DepotCatalog catalog = DD1DepotCatalog.of(Arrays.asList(
            new DD1DepotCatalog.Row(262061, 0, "windows", "aaa"),
            new DD1DepotCatalog.Row(580100, 580100, "windows", "bbb")));

        assertEquals(0, catalog.depotOf(262061));
        assertEquals(580100, catalog.depotOf(580100));
    }

    @Test
    public void aDlcWithNoWindowsDepotIsNotGuessedAt() {
        DD1DepotCatalog catalog = DD1DepotCatalog.of(Arrays.asList(
            new DD1DepotCatalog.Row(999001, 999000, "macos", "aaa"),
            new DD1DepotCatalog.Row(999002, 999000, "linux", "bbb")));

        assertEquals(0, catalog.depotOf(999000));
        assertNull(catalog.manifestOf(999000));
    }

    @Test
    public void anAccountWithNothingReadYetKnowsNothing() {
        assertEquals(0, DD1DepotCatalog.empty().depotOf(580100));
        assertNull(DD1DepotCatalog.empty().manifestOf(580100));
    }
}
