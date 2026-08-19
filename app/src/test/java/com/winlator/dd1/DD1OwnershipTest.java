package com.winlator.dd1;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.util.Collections;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class DD1OwnershipTest {
    @Test
    public void findsOwnershipInsideLicensedPackageApps() {
        Map<Integer, List<Integer>> packages = new HashMap<>();
        packages.put(123, Arrays.asList(10, 262060, 20));

        assertTrue(DD1Ownership.ownsApp(packages, 262060));
        assertFalse(DD1Ownership.ownsApp(packages, 99));
    }

    @Test
    public void emptyPackageMetadataDoesNotGrantOwnership() {
        assertFalse(DD1Ownership.ownsApp(new HashMap<>(), 262060));
    }

    @Test
    public void gathersAppsFromEveryPackageBecauseDlcShipsSeparately() {
        Map<Integer, List<Integer>> packages = new HashMap<>();
        packages.put(1, Arrays.asList(262060));
        packages.put(2, Arrays.asList(580100));
        packages.put(3, Arrays.asList(580100, 735730));

        assertEquals(Arrays.asList(262060, 580100, 735730), DD1Ownership.ownedAppIds(packages));
    }

    @Test
    public void noPackagesMeansNoApps() {
        assertEquals(Collections.emptyList(), DD1Ownership.ownedAppIds(new HashMap<>()));
    }
}
