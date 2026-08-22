package com.winlator.dd1;

import com.winlator.container.GraphicsDrivers;

import java.util.Locale;

// Which pair of drivers a new profile starts on: one for Vulkan, one for OpenGL.
//
// Turnip is Mesa's driver for Adreno and only for Adreno - the launcher decides on
// the name because the runtime reads a model number instead, and only accepts
// 6xx, 7xx and 8xx, so a newer Adreno was taken for a foreign GPU.
//
// Everything else gets Vortek, which is not a driver but a bridge: it serialises
// the guest's Vulkan calls over a socket to the device's own driver. On Samsung's
// Xclipse that driver is missing extensions the GL-on-Vulkan layers need, and Wine
// answers by turning 3D off - the interface draws and the world does not. VirGL is
// Mesa's own renderer and the one thing that does work on those parts, at the cost
// of speed this game can afford: it is a 2016 title that spends most of its time
// drawing flat sprites.
public abstract class DD1GraphicsDriver {
    private DD1GraphicsDriver() {}

    public static String forRenderer(String renderer) {
        boolean adreno = renderer != null
            && renderer.toLowerCase(Locale.ROOT).contains("adreno");
        return adreno
            ? GraphicsDrivers.TURNIP + "," + GraphicsDrivers.GLADIO
            : GraphicsDrivers.VORTEK + "," + GraphicsDrivers.VIRGL;
    }
}
