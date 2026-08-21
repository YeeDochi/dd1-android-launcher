package com.winlator.dd1;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class DD1WorkshopNotificationTest {
    @Test
    public void syncingNotificationIncludesPercent() {
        assertEquals("Plague Doctor skins mod · 42%",
            DD1InstallService.workshopNotificationText("Plague Doctor skins mod", 42));
    }
}
