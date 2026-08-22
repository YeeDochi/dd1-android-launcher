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

    @Test
    public void everyOfferedPairIsAcceptedBack() {
        for (String pair : DD1GraphicsChoice.PAIRS)
            assertEquals(pair, DD1GraphicsChoice.resolve(pair, "Adreno (TM) 830"));
        assertTrue(DD1GraphicsChoice.PAIRS.contains("turnip,gladio"));
        assertTrue(DD1GraphicsChoice.PAIRS.contains("vortek,virgl"));
    }
}
