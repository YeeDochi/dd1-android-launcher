package io.github.dd1android.launcher.runtime;

import io.github.dd1android.launcher.storage.AppPaths;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class LaunchConfigFactory {
    private LaunchConfigFactory() {}

    public static LaunchConfig create(DeviceCaps caps, AppPaths paths, Renderer requested) {
        Renderer renderer = requested != null
                ? requested
                : caps.waydroid() ? Renderer.WAYDROID_MESA : Renderer.ZINK;
        Renderer fallback = requested == null && renderer == Renderer.ZINK
                ? Renderer.MOBILE_GLUES
                : null;

        Path game = paths.game().toPath().toAbsolutePath();
        Path runtime = paths.runtime().toPath().toAbsolutePath();
        Path saves = paths.saves().toPath().toAbsolutePath();
        Map<String, String> rendererEnvironment = rendererEnvironment(renderer, runtime);
        Map<String, String> environment = new LinkedHashMap<>();
        environment.put("HOME", saves.toString());
        environment.put("XDG_CONFIG_HOME", saves.resolve("config").toString());
        environment.put("XDG_DATA_HOME", saves.resolve("data").toString());
        environment.put("XDG_CACHE_HOME", paths.cache().getAbsolutePath());
        environment.put("BOX64_LOG_FILE", new java.io.File(paths.logs(), "box64.log").getAbsolutePath());
        environment.put("BOX64_DYNACACHE", "1");
        environment.put("BOX64_LD_LIBRARY_PATH",
                game.resolve("_linuxnosteam/lib64") + ":" + runtime.resolve("lib"));
        environment.put("DISPLAY", ":0");
        environment.putAll(rendererEnvironment);

        return new LaunchConfig(
                game.resolve("_linuxnosteam/darkest.bin.x86_64"),
                game,
                environment,
                rendererEnvironment,
                1280,
                720,
                renderer,
                fallback);
    }

    private static Map<String, String> rendererEnvironment(Renderer renderer, Path runtime) {
        return switch (renderer) {
            case ZINK -> Map.of(
                    "GALLIUM_DRIVER", "zink",
                    "MESA_LOADER_DRIVER_OVERRIDE", "zink",
                    "BOX64_LIBGL", runtime.resolve("lib/libGL.so.1").toString());
            case MOBILE_GLUES -> Map.of(
                    "BOX64_LIBGL", runtime.resolve("lib/libmobileglues.so").toString());
            case WAYDROID_MESA -> Map.of();
        };
    }
}
