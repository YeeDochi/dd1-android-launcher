package com.winlator.dd1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.winlator.core.EnvVars;

import org.junit.Test;

public class DD1GladioClientTest {
    private static final String CLIENT = "/data/data/com.winlator/files/rootfs/opt/dd1-gladio";
    private static final String LIB = "/data/data/com.winlator/files/rootfs/lib";

    @Test
    public void onlyTheGladioPairsUseTheClient() {
        assertTrue(DD1GladioClient.usesGladio("turnip,gladio"));
        assertTrue(DD1GladioClient.usesGladio("vortek,gladio"));
        assertFalse(DD1GladioClient.usesGladio("turnip,zink"));
        assertFalse(DD1GladioClient.usesGladio("vortek,virgl"));
        assertFalse(DD1GladioClient.usesGladio(null));
    }

    @Test
    public void theClientGoesFirstAndKeepsTheRootfsLibraries() {
        EnvVars vars = new EnvVars(DD1GladioClient.withLibraryPath("WINEDEBUG=-all", CLIENT, LIB));
        assertEquals(CLIENT + ":" + LIB, vars.get("LD_LIBRARY_PATH"));
        assertEquals("-all", vars.get("WINEDEBUG"));
    }

    @Test
    public void ourLibraryPathIsRemovedWhenTheClientIsNotUsed() {
        String ours = DD1GladioClient.withLibraryPath("", CLIENT, LIB);
        assertFalse(new EnvVars(DD1GladioClient.withLibraryPath(ours, null, LIB)).has("LD_LIBRARY_PATH"));
    }

    @Test
    public void someoneElsesLibraryPathIsLeftAlone() {
        String theirs = "LD_LIBRARY_PATH=/opt/other";
        assertEquals("/opt/other",
            new EnvVars(DD1GladioClient.withLibraryPath(theirs, null, LIB)).get("LD_LIBRARY_PATH"));
    }

    @Test
    public void theFrameLimitIsPassedAndNoLimitIsZero() {
        assertEquals("30", new EnvVars(DD1GladioClient.withFrameLimit("", 30)).get("DD1_FPS_LIMIT"));
        assertEquals("0", new EnvVars(DD1GladioClient.withFrameLimit("DD1_FPS_LIMIT=30", 0)).get("DD1_FPS_LIMIT"));
    }

    @Test
    public void theFrameLimitGoesWithTheClient() {
        assertFalse(new EnvVars(DD1GladioClient.withFrameLimit("DD1_FPS_LIMIT=60", null)).has("DD1_FPS_LIMIT"));
    }

    @Test
    public void sixtyIsTheDefaultAndAChoice() {
        boolean listed = false;
        for (int fps : DD1GladioClient.FRAME_LIMITS) listed |= fps == DD1GladioClient.DEFAULT_FRAME_LIMIT;
        assertTrue(listed);
        assertEquals(60, DD1GladioClient.DEFAULT_FRAME_LIMIT);
    }
}
