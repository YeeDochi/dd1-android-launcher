package com.winlator.dd1;

import java.util.LinkedHashMap;
import java.util.Map;

public abstract class DD1ProfileConfig {
    // Darkest Dungeon draws with OpenGL, plays sound through FMOD and imports only
    // the 2015+ C runtime, which the game's own redistributable installs. None of
    // the native Windows components or a Direct3D translation layer is touched,
    // and each one that is switched on is unpacked into the prefix at the first
    // launch - DXVK, 31 MB of Direct3D DLLs, XAudio - for nothing. Wine's built-in
    // versions stay in place of all of them.
    static final String DXWRAPPER = "wined3d";
    static final String WINCOMPONENTS = "direct3d=0,directsound=0,directmusic=0,directshow=0,"
        + "directplay=0,xaudio=0,vcrun2005=0,vcrun2010=0,wmdecoder=0";

    // Brings a profile made by an earlier build in line. Switching a component
    // off restores Wine's built-in DLLs, and switching to wined3d does the same,
    // so the runtime needs none of the files left out of the APK to do it.
    public static boolean trim(com.winlator.container.Container container) {
        boolean changed = false;
        if (!DXWRAPPER.equals(container.getDXWrapper())) {
            container.setDXWrapper(DXWRAPPER);
            changed = true;
        }
        if (!WINCOMPONENTS.equals(container.getWinComponents())) {
            container.setWinComponents(WINCOMPONENTS);
            changed = true;
        }
        return changed;
    }

    public static Map<String, Object> create(String graphicsDriver, String cpuList) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("name", "Darkest Dungeon");
        // The game scales its interface with the resolution, so this does not
        // change how big the text is - only how many pixels it gets. 1080 matches
        // the S25 panel exactly and reads cleanly; 960x540 was the same size and
        // blurrier, and anything taller is downscaled again. The bars either side
        // are where the Esc button lives.
        config.put("screenSize", "1920x1080");
        config.put("envVars", "ZINK_DESCRIPTORS=lazy ZINK_DEBUG=compact MESA_SHADER_CACHE_DISABLE=false MESA_SHADER_CACHE_MAX_SIZE=512MB mesa_glthread=true WINEESYNC=1 TU_DEBUG=noconform");
        config.put("cpuList", cpuList);
        config.put("cpuListWoW64", cpuList);
        config.put("graphicsDriver", graphicsDriver);
        config.put("dxwrapper", DXWRAPPER);
        config.put("audioDriver", "alsa");
        config.put("wincomponents", WINCOMPONENTS);
        config.put("hudMode", (byte)0);
        config.put("startupSelection", (byte)1);
        config.put("box64Preset", "INTERMEDIATE");
        config.put("desktopTheme", "LIGHT,IMAGE,#0277bd");
        return config;
    }
}
