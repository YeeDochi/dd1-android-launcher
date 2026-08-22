package com.winlator.dd1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DD1UpdateTest {
    @Test
    public void readsTheTagOffARelease() {
        assertEquals("0.1.9", DD1Update.parseTag(
            "{\"url\":\"https://api.github.com/x\",\"tag_name\":\"v0.1.9\",\"name\":\"0.1.9\"}"));
    }

    // The shape GitHub actually answers with: pretty-printed, a space after the
    // colon, the tag far from the front of a large document.
    @Test
    public void readsTheTagOffTheAnswerGithubReallyGives() {
        assertEquals("0.1.8", DD1Update.parseTag(
            "{\n  \"url\": \"https://api.github.com/repos/x/y/releases/374835915\",\n"
            + "  \"tag_name\": \"v0.1.8\",\n  \"name\": \"0.1.8\"\n}"));
    }

    @Test
    public void keepsATagThatWasNotPrefixed() {
        assertEquals("0.2.0", DD1Update.parseTag("{\"tag_name\":\"0.2.0\"}"));
    }

    @Test
    public void hasNoAnswerWithoutOne() {
        assertNull(DD1Update.parseTag(null));
        assertNull(DD1Update.parseTag("{\"message\":\"Not Found\"}"));
        assertNull(DD1Update.parseTag("{\"tag_name\":\"\"}"));
    }

    @Test
    public void countsTheTenthReleaseAsNewerThanTheNinth() {
        assertTrue(DD1Update.newerThan("0.1.9", "0.1.10"));
        assertFalse(DD1Update.newerThan("0.1.10", "0.1.9"));
    }

    @Test
    public void saysNothingAboutTheVersionAlreadyInstalled() {
        assertFalse(DD1Update.newerThan("0.1.8", "0.1.8"));
        assertFalse(DD1Update.newerThan("0.1.8", "0.1.7"));
        assertTrue(DD1Update.newerThan("0.1.8", "0.1.9"));
        assertTrue(DD1Update.newerThan("0.1.8", "0.2.0"));
        assertTrue(DD1Update.newerThan("0.1.8", "1.0"));
    }

    @Test
    public void readsAShorterVersionAsZeroesRatherThanAsNewer() {
        assertFalse(DD1Update.newerThan("0.1.8", "0.1"));
        assertTrue(DD1Update.newerThan("0.1", "0.1.8"));
        assertFalse(DD1Update.newerThan("0.1.8", "0.1.8.0"));
    }

    // A tag nobody expected must not announce an update that does not exist.
    @Test
    public void doesNotReadNonsenseAsANewerVersion() {
        assertFalse(DD1Update.newerThan("0.1.8", "nightly"));
        assertFalse(DD1Update.newerThan("0.1.8", ""));
    }
}
