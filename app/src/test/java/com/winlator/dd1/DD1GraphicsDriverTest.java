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

    // Turnip is Adreno's and nothing else's, and the bridge to a Mali or Xclipse
    // driver cannot serve the GL-on-Vulkan layers - the extensions are not there,
    // Wine turns 3D off, and the game draws its interface over a black world.
    // VirGL is Mesa's own renderer and works on those parts.
    @Test
    public void aGpuThatIsNotAnAdrenoGetsVirglOverVortek() {
        assertEquals("vortek,virgl", DD1GraphicsDriver.forRenderer("Mali-G720"));
        assertEquals("vortek,virgl", DD1GraphicsDriver.forRenderer("Samsung Xclipse 950"));
        assertEquals("vortek,virgl", DD1GraphicsDriver.forRenderer("Samsung Xclipse 960"));
    }

    // A renderer string the launcher could not read must not read as Adreno.
    // A renderer string the launcher could not read must not read as Adreno: the
    // pair that works on the widest range of parts is the safer answer.
    @Test
    public void nothingKnownGetsTheOneThatWorksOnMostThings() {
        assertEquals("vortek,virgl", DD1GraphicsDriver.forRenderer(null));
        assertEquals("vortek,virgl", DD1GraphicsDriver.forRenderer(""));
    }
}
