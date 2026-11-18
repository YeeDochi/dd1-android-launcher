package com.winlator.dd1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

public class DD1VulkanDriverTest {
    // The name becomes a directory under the app's files; it must not leave it.
    // (The manifest itself is read with org.json, which the unit tests do not
    // have; reading it is the runtime's own code.)
    @Test
    public void aNameThatClimbsOutIsRefused() {
        for (String name : new String[]{"../x", "a/b", "..", "a\\b"}) {
            try {
                DD1VulkanDriver.checkedName(name);
                fail(name);
            }
            catch (DD1VulkanDriver.NotADriver expected) {}
        }
    }

    @Test
    public void anOrdinaryNameIsKeptTrimmed() throws Exception {
        assertEquals("Turnip Gen8 V36", DD1VulkanDriver.checkedName(" Turnip Gen8 V36 "));
    }

    // Only the Vulkan half of "vulkan|opengl" changes; what VirGL or Zink was
    // told stays.
    @Test
    public void choosingADriverKeepsTheOpenGLSettings() {
        assertEquals("adrenotoolsDriver=Gen8|",
            DD1VulkanDriver.withDriver("vortek,zink", "", "Gen8"));
        assertEquals("vkMaxVersion=1.3,adrenotoolsDriver=Gen8|glVersion=3.1",
            DD1VulkanDriver.withDriver("vortek,virgl", "vkMaxVersion=1.3|glVersion=3.1", "Gen8"));
        assertEquals("adrenotoolsDriver=System|",
            DD1VulkanDriver.withDriver("vortek,zink", "adrenotoolsDriver=Gen8|", null));
    }

    @Test
    public void onlyVortekLoadsADriverFile() {
        assertTrue(DD1VulkanDriver.appliesTo("vortek,zink"));
        assertTrue(DD1VulkanDriver.appliesTo("vortek,gladio"));
        assertFalse(DD1VulkanDriver.appliesTo("turnip,gladio"));
        assertFalse(DD1VulkanDriver.appliesTo(null));
    }
}
