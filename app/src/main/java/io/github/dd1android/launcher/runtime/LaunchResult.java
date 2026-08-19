package io.github.dd1android.launcher.runtime;

import java.nio.file.Path;

public record LaunchResult(
        int exitCode,
        Renderer renderer,
        boolean firstFrame,
        long elapsedMillis,
        Path logDirectory) {}
