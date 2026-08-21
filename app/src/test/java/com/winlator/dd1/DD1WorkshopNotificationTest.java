package com.winlator.dd1;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class DD1WorkshopNotificationTest {
    @Test
    public void syncingNotificationIncludesPercent() {
        assertEquals("Plague Doctor skins mod · 42%",
            DD1InstallService.workshopNotificationText(1, 1, "Plague Doctor skins mod", 42));
    }

    // A PC session can leave a dozen new subscriptions waiting. They download one
    // at a time, so the notification says which one of how many is in hand.
    @Test
    public void queuedNotificationCountsTheQueue() {
        assertEquals("3/12 · Plague Doctor skins mod · 42%",
            DD1InstallService.workshopNotificationText(3, 12, "Plague Doctor skins mod", 42));
    }
}
