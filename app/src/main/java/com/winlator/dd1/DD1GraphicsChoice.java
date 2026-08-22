package com.winlator.dd1;

import android.content.Context;
import android.content.SharedPreferences;

import com.winlator.container.GraphicsDrivers;
import com.winlator.core.GPUHelper;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

// Which drivers the game runs on, and who decided. Left alone the launcher reads
// the GPU's name and picks the pair known to work on that family; a device the
// automatic answer does not serve can be told directly, once, from the settings
// screen. Only pairs that are known to go together are offered - the runtime
// takes one driver for Vulkan and one for OpenGL, and most of the nine
// combinations are answers to nobody's question.
public abstract class DD1GraphicsChoice {
    private DD1GraphicsChoice() {}

    public static final String AUTOMATIC = "";
    public static final String KEY = "graphics_driver";

    public static final List<String> PAIRS = Collections.unmodifiableList(Arrays.asList(
        GraphicsDrivers.TURNIP + "," + GraphicsDrivers.GLADIO,
        GraphicsDrivers.TURNIP + "," + GraphicsDrivers.ZINK,
        GraphicsDrivers.VORTEK + "," + GraphicsDrivers.VIRGL,
        GraphicsDrivers.VORTEK + "," + GraphicsDrivers.GLADIO));

    // Pure so it can be tested without a device: what was stored, and what the GPU
    // says it is.
    public static String resolve(String stored, String renderer) {
        return stored != null && PAIRS.contains(stored)
            ? stored : DD1GraphicsDriver.forRenderer(renderer);
    }

    public static String resolve(Context context) {
        return resolve(stored(context), GPUHelper.glGetRenderer(context));
    }

    public static String stored(Context context) {
        return preferences(context).getString(KEY, AUTOMATIC);
    }

    public static void store(Context context, String pair) {
        preferences(context).edit().putString(KEY,
            pair == null || !PAIRS.contains(pair) ? AUTOMATIC : pair).apply();
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences("dd1", Context.MODE_PRIVATE);
    }
}
