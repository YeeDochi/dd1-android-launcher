package com.winlator.dd1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class DD1LocaleTest {
    @Test
    public void noChoiceLeavesItToTheSystem() {
        assertNull(DD1Locale.localeFor(DD1Locale.SYSTEM));
        assertNull(DD1Locale.localeFor(null));
    }

    @Test
    public void aTagNamesTheLanguage() {
        assertEquals("ko", DD1Locale.localeFor("ko").getLanguage());
        assertEquals("en", DD1Locale.localeFor("en").getLanguage());
    }

    @Test
    public void nonsenseFallsBackToTheSystemRatherThanAnEmptyLocale() {
        assertNull(DD1Locale.localeFor("!!!"));
    }
}
