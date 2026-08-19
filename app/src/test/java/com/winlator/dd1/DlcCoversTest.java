package com.winlator.dd1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class DlcCoversTest {
    @Test
    public void readsTheArtworkAddressOutOfTheStoreResponse() {
        String json = "{\"4964110\":{\"success\":true,\"data\":{\"type\":\"dlc\","
            + "\"header_image\":\"https:\\/\\/shared.akamai.steamstatic.com\\/a\\/header.jpg?t=1\"}}}";

        assertEquals("https://shared.akamai.steamstatic.com/a/header.jpg?t=1",
            DlcCovers.parseHeaderUrl(json));
    }

    @Test
    public void aResponseWithoutArtworkYieldsNothing() {
        assertNull(DlcCovers.parseHeaderUrl("{\"4964110\":{\"success\":false}}"));
        assertNull(DlcCovers.parseHeaderUrl(""));
    }
}
