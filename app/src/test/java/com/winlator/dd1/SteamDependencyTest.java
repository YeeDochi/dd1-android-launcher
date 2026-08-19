package com.winlator.dd1;

import static org.junit.Assert.assertEquals;

import in.dragonbra.javasteam.depotdownloader.data.AppItem;
import in.dragonbra.javasteam.steam.authentication.AuthSessionDetails;

import java.util.Collections;

import org.junit.Test;

public class SteamDependencyTest {
    @Test
    public void createsWindowsDd1DownloadRequest() {
        AuthSessionDetails auth = new AuthSessionDetails();
        auth.persistentSession = true;
        AppItem item = new AppItem(262060, false, "/tmp/game", "public", "",
            false, "windows", false, "64", false, "english", false,
            Collections.emptyList(), Collections.emptyList(), true, false);

        assertEquals(262060, item.getAppId());
        assertEquals("windows", item.getOs());
    }
}
