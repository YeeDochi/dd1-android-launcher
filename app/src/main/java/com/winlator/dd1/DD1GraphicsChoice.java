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
// screen. The presets are the pairs known to go together; the advanced section
// takes any Vulkan driver with any OpenGL one, for people who know the runtime
// from Winlator and want a pair the presets leave out.
public abstract class DD1GraphicsChoice {
    private DD1GraphicsChoice() {}

    public static final String AUTOMATIC = "";
    public static final String KEY = "graphics_driver";

    public static final List<String> PAIRS = Collections.unmodifiableList(Arrays.asList(
        GraphicsDrivers.TURNIP + "," + GraphicsDrivers.GLADIO,
        GraphicsDrivers.TURNIP + "," + GraphicsDrivers.ZINK,
        // For an Adreno that Turnip serves badly: Qualcomm's own driver under Zink
        // was the best of every pair on a Galaxy S26 Ultra (issue #5).
        GraphicsDrivers.VORTEK + "," + GraphicsDrivers.ZINK,
        GraphicsDrivers.VORTEK + "," + GraphicsDrivers.VIRGL,
        GraphicsDrivers.VORTEK + "," + GraphicsDrivers.GLADIO));

    // What the runtime takes, one of each, as "vulkan,opengl".
    public static final List<String> VULKAN = Collections.unmodifiableList(Arrays.asList(
        GraphicsDrivers.TURNIP, GraphicsDrivers.VORTEK));
    public static final List<String> OPENGL = Collections.unmodifiableList(Arrays.asList(
        GraphicsDrivers.GLADIO, GraphicsDrivers.ZINK, GraphicsDrivers.VIRGL));

    public static boolean accepted(String pair) {
        if (pair == null) return false;
        String[] parts = pair.split(",", -1);
        return parts.length == 2 && VULKAN.contains(parts[0]) && OPENGL.contains(parts[1]);
    }

    public static String pair(String vulkan, String opengl) {
        return vulkan + "," + opengl;
    }

    public static String vulkanOf(String pair) {
        return pair.substring(0, pair.indexOf(','));
    }

    public static String openglOf(String pair) {
        return pair.substring(pair.indexOf(',') + 1);
    }

    // Pure so it can be tested without a device: what was stored, and what the GPU
    // says it is.
    public static String resolve(String stored, String renderer) {
        return accepted(stored)
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
            accepted(pair) ? pair : AUTOMATIC).apply();
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences("dd1", Context.MODE_PRIVATE);
    }
}
