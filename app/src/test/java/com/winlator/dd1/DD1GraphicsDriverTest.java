package com.winlator.dd1;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class DD1GraphicsDriverTest {
    @Test
    public void theAdrenoOnTheDeviceThisWasBuiltOnStillGetsTurnip() {
        assertEquals("turnip,gladio", DD1GraphicsDriver.forRenderer("Adreno (TM) 830"));
    }

    // The runtime's own check only accepts 6xx, 7xx and 8xx, so every one of
    // these was being handed Vortek on hardware Turnip is written for.
    @Test
    public void anAdrenoNamedSomethingElseIsStillAnAdreno() {
        assertEquals("turnip,gladio", DD1GraphicsDriver.forRenderer("Adreno (TM) 940"));
        assertEquals("turnip,gladio", DD1GraphicsDriver.forRenderer("Adreno (TM) 1100"));
        assertEquals("turnip,gladio", DD1GraphicsDriver.forRenderer("Adreno (TM) X2-85"));
        assertEquals("turnip,gladio", DD1GraphicsDriver.forRenderer("adreno 8 Elite"));
    }

    @Test
    public void aGpuThatIsNotAnAdrenoGetsVortek() {
        assertEquals("vortek,gladio", DD1GraphicsDriver.forRenderer("Mali-G720"));
        assertEquals("vortek,gladio", DD1GraphicsDriver.forRenderer("Samsung Xclipse 950"));
    }

    // A renderer string the launcher could not read must not read as Adreno.
    @Test
    public void nothingKnownGetsVortek() {
        assertEquals("vortek,gladio", DD1GraphicsDriver.forRenderer(null));
        assertEquals("vortek,gladio", DD1GraphicsDriver.forRenderer(""));
    }
}
