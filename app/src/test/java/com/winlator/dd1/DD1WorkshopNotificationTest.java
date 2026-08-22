package com.winlator.dd1;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class DD1WorkshopNotificationTest {
    @Test
    public void syncingNotificationIncludesPercent() {
        assertEquals("Plague Doctor skins mod · 42%",
            DD1InstallService.workshopNotificationText("Plague Doctor skins mod", 42));
    }

    // A PC session can leave a dozen new subscriptions waiting, and pressing
    // subscribe twice makes a queue of two. They download one at a time, so the
    // message says which one of how many is in hand - and the shade carries that
    // message rather than working the count out a second time.
    @Test
    public void aQueuedDownloadCarriesItsPositionThrough() {
        assertEquals("3/12 · Plague Doctor skins mod · 42%",
            DD1InstallService.workshopNotificationText("3/12 · Plague Doctor skins mod", 42));
    }

    @Test
    public void aPercentOutOfRangeIsClamped() {
        assertEquals("mod · 100%", DD1InstallService.workshopNotificationText("mod", 140));
        assertEquals("mod · 0%", DD1InstallService.workshopNotificationText("mod", -3));
    }
}
