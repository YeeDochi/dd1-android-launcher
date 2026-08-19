package io.github.dd1android.launcher.runtime;

import java.nio.file.Path;
import java.util.Map;

public record LaunchConfig(
        Path executable,
        Path workingDirectory,
        Map<String, String> environment,
        Map<String, String> rendererEnvironment,
        int width,
        int height,
        Renderer renderer,
        Renderer fallbackRenderer) {
    public LaunchConfig {
        environment = Map.copyOf(environment);
        rendererEnvironment = Map.copyOf(rendererEnvironment);
    }
}
