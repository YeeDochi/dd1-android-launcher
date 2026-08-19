package com.winlator.dd1;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

public class DD1SteamEventsTest {
    @Test
    public void ownedPackagesEnableInstallOnlyAfterLoginAndPics() {
        DD1SteamEvents events = new DD1SteamEvents();

        assertEquals(DD1InstallPhase.AUTHENTICATING, events.authStarted("url").phase);
        assertEquals(DD1InstallPhase.AUTHENTICATING, events.loggedOn().phase);
        assertEquals(DD1InstallPhase.READY_TO_INSTALL,
            events.packagesResolved(Collections.singletonMap(1, Arrays.asList(262060))).phase);
    }

    @Test
    public void unownedPackagesNeverEnableDownload() {
        DD1SteamEvents events = new DD1SteamEvents();
        events.loggedOn();

        assertEquals(DD1InstallPhase.NOT_OWNED,
            events.packagesResolved(Collections.singletonMap(1, Arrays.asList(10))).phase);
    }

    @Test
    public void errorsDoNotExposeSecretText() {
        DD1SteamEvents events = new DD1SteamEvents();

        DD1InstallSnapshot snapshot = events.failed("access_token=secret");

        assertEquals(DD1InstallPhase.ERROR, snapshot.phase);
        assertEquals("Steam operation failed", snapshot.message);
    }
}
