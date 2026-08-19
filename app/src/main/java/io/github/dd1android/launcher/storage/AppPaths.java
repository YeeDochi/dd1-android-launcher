package io.github.dd1android.launcher.storage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public record AppPaths(File game, File runtime, File saves, File mods, File cache, File logs) {
    public static AppPaths create(File filesDir) throws IOException {
        AppPaths paths = new AppPaths(
                new File(filesDir, "game"),
                new File(filesDir, "runtime"),
                new File(filesDir, "saves"),
                new File(filesDir, "mods"),
                new File(filesDir, "cache"),
                new File(filesDir, "logs"));
        for (File directory : new File[] {
                paths.game, paths.runtime, paths.saves, paths.mods, paths.cache, paths.logs
        }) {
            Files.createDirectories(directory.toPath());
        }
        return paths;
    }
}
