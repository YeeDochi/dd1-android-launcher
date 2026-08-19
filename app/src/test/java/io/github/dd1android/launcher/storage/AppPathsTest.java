package io.github.dd1android.launcher.storage;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public final class AppPathsTest {
    @Test
    public void createsRequiredPrivateDirectories() throws Exception {
        Path files = Files.createTempDirectory("dd1-files");

        AppPaths.create(files.toFile());

        for (String name : new String[] {"game", "runtime", "saves", "mods", "cache", "logs"}) {
            assertTrue(Files.isDirectory(files.resolve(name)));
        }
    }
}
