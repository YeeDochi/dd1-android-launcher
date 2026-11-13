package com.winlator.dd1;

import static org.junit.Assert.assertEquals;

import java.util.Map;

import org.junit.Test;

public class DD1ProfileConfigTest {
    @Test
    public void createsTheSingleAutomaticRuntimeProfile() {
        Map<String, Object> config = DD1ProfileConfig.create("turnip,gladio", "0,1,2,3");

        assertEquals("Darkest Dungeon", config.get("name"));
        // 1:1 with the S25 panel: the game scales its interface with the
        // resolution, so a smaller one is the same size and blurrier.
        assertEquals("1920x1080", config.get("screenSize"));
        assertEquals("turnip,gladio", config.get("graphicsDriver"));
        assertEquals("0,1,2,3", config.get("cpuList"));
        assertEquals("0,1,2,3", config.get("cpuListWoW64"));
        // OpenGL, FMOD and the 2015+ runtime: no Direct3D layer, no native
        // Windows component, and so nothing of them unpacked at the first launch.
        assertEquals("wined3d", config.get("dxwrapper"));
        assertEquals(false, String.valueOf(config.get("wincomponents")).contains("=1"));
        assertEquals("alsa", config.get("audioDriver"));
        assertEquals((byte)1, config.get("startupSelection"));
        assertEquals("INTERMEDIATE", config.get("box64Preset"));
    }
}
