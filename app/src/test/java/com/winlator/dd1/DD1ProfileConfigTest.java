package com.winlator.dd1;

import static org.junit.Assert.assertEquals;

import java.util.Map;

import org.junit.Test;

public class DD1ProfileConfigTest {
    @Test
    public void createsTheSingleAutomaticRuntimeProfile() {
        Map<String, Object> config = DD1ProfileConfig.create("turnip,gladio", "0,1,2,3");

        assertEquals("Darkest Dungeon", config.get("name"));
        assertEquals("1280x720", config.get("screenSize"));
        assertEquals("turnip,gladio", config.get("graphicsDriver"));
        assertEquals("0,1,2,3", config.get("cpuList"));
        assertEquals("0,1,2,3", config.get("cpuListWoW64"));
        assertEquals("dxvk", config.get("dxwrapper"));
        assertEquals("alsa", config.get("audioDriver"));
        assertEquals((byte)1, config.get("startupSelection"));
        assertEquals("INTERMEDIATE", config.get("box64Preset"));
    }
}
