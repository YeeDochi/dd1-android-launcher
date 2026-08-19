package com.winlator.dd1;

import java.util.LinkedHashMap;
import java.util.Map;

public abstract class DD1ProfileConfig {
    public static Map<String, Object> create(String graphicsDriver, String cpuList) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("name", "Darkest Dungeon");
        config.put("screenSize", "1280x720");
        config.put("envVars", "ZINK_DESCRIPTORS=lazy ZINK_DEBUG=compact MESA_SHADER_CACHE_DISABLE=false MESA_SHADER_CACHE_MAX_SIZE=512MB mesa_glthread=true WINEESYNC=1 TU_DEBUG=noconform");
        config.put("cpuList", cpuList);
        config.put("cpuListWoW64", cpuList);
        config.put("graphicsDriver", graphicsDriver);
        config.put("dxwrapper", "dxvk");
        config.put("audioDriver", "alsa");
        config.put("wincomponents", "direct3d=1,directsound=1,directmusic=1,directshow=0,directplay=0,xaudio=1,vcrun2005=0,vcrun2010=1,wmdecoder=1");
        config.put("hudMode", (byte)0);
        config.put("startupSelection", (byte)1);
        config.put("box64Preset", "INTERMEDIATE");
        config.put("desktopTheme", "LIGHT,IMAGE,#0277bd");
        return config;
    }
}
