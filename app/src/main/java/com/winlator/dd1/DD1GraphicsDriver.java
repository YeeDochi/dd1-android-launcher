package com.winlator.dd1;

import com.winlator.container.GraphicsDrivers;

import java.util.Locale;

// Which pair of drivers a new profile starts on. The runtime decides this by
// reading a model number out of the renderer string, and only 6xx, 7xx and 8xx
// count - so an Adreno called anything else is taken for a foreign GPU and given
// Vortek, on a profile whose environment is tuned for Turnip. The name is enough:
// Turnip is the Adreno driver whatever the number after it says.
public abstract class DD1GraphicsDriver {
    private DD1GraphicsDriver() {}

    public static String forRenderer(String renderer) {
        boolean adreno = renderer != null
            && renderer.toLowerCase(Locale.ROOT).contains("adreno");
        return (adreno ? GraphicsDrivers.TURNIP : GraphicsDrivers.VORTEK)
            + "," + GraphicsDrivers.DEFAULT_OPENGL_DRIVER;
    }
}
