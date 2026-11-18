package com.winlator.dd1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DD1GraphicsChoiceTest {
    @Test
    public void nothingChosenLetsTheDeviceDecide() {
        assertEquals("turnip,gladio",
            DD1GraphicsChoice.resolve(null, "Adreno (TM) 830"));
        assertEquals("vortek,virgl",
            DD1GraphicsChoice.resolve("", "Samsung Xclipse 960"));
        assertEquals("vortek,virgl",
            DD1GraphicsChoice.resolve(DD1GraphicsChoice.AUTOMATIC, "Mali-G720"));
    }

    // The point of the list: a device the automatic answer does not serve can be
    // told what to use, and told it once.
    @Test
    public void achoiceOverridesWhateverTheDeviceIs() {
        assertEquals("vortek,virgl",
            DD1GraphicsChoice.resolve("vortek,virgl", "Adreno (TM) 830"));
        assertEquals("turnip,gladio",
            DD1GraphicsChoice.resolve("turnip,gladio", "Samsung Xclipse 960"));
    }

    // A value from an older build, or one somebody typed into the preferences by
    // hand, must not leave the game with a driver that does not exist.
    @Test
    public void aStoredValueThatIsNotOnTheListIsIgnored() {
        assertEquals("turnip,gladio",
            DD1GraphicsChoice.resolve("nonsense", "Adreno (TM) 830"));
        assertEquals("vortek,virgl",
            DD1GraphicsChoice.resolve("turnip,turnip", "Mali-G720"));
    }

    // The advanced section: any Vulkan driver with any OpenGL one, including a
    // pair no preset names.
    @Test
    public void anyVulkanWithAnyOpenGLIsAccepted() {
        for (String vulkan : DD1GraphicsChoice.VULKAN)
            for (String opengl : DD1GraphicsChoice.OPENGL) {
                String pair = DD1GraphicsChoice.pair(vulkan, opengl);
                assertEquals(pair, DD1GraphicsChoice.resolve(pair, "Mali-G720"));
                assertEquals(vulkan, DD1GraphicsChoice.vulkanOf(pair));
                assertEquals(opengl, DD1GraphicsChoice.openglOf(pair));
            }
        assertEquals("turnip,virgl", DD1GraphicsChoice.resolve("turnip,virgl", "Mali-G720"));
        assertEquals("vortek,virgl", DD1GraphicsChoice.resolve("gladio,turnip", "Mali-G720"));
        assertEquals("vortek,virgl", DD1GraphicsChoice.resolve("turnip,gladio,zink", "Mali-G720"));
    }

    @Test
    public void everyOfferedPairIsAcceptedBack() {
        for (String pair : DD1GraphicsChoice.PAIRS)
            assertEquals(pair, DD1GraphicsChoice.resolve(pair, "Adreno (TM) 830"));
        assertTrue(DD1GraphicsChoice.PAIRS.contains("turnip,gladio"));
        assertTrue(DD1GraphicsChoice.PAIRS.contains("vortek,virgl"));
    }

    // Offered for an Adreno that Turnip serves badly (issue #5), and only offered:
    // the automatic answer for an Adreno stays Turnip.
    @Test
    public void qualcommsOwnDriverUnderZinkCanBeChosenButIsNotTheDefault() {
        assertTrue(DD1GraphicsChoice.PAIRS.contains("vortek,zink"));
        assertEquals("vortek,zink",
            DD1GraphicsChoice.resolve("vortek,zink", "Adreno (TM) 840"));
        assertEquals("turnip,gladio",
            DD1GraphicsChoice.resolve(null, "Adreno (TM) 840"));
    }
}
