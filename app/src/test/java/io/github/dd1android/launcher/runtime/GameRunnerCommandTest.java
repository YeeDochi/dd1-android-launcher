package io.github.dd1android.launcher.runtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import io.github.dd1android.launcher.storage.AppPaths;
import java.nio.file.Files;
import org.junit.Test;

public final class GameRunnerCommandTest {
    @Test
    public void arm64SelectsBox64Runner() throws Exception {
        LaunchConfig config = config(false);

        RunnerCommand command = RunnerCommand.forAbi("arm64-v8a", config);

        assertEquals("libdd1_runner.so", command.packagedExecutable());
        assertEquals(config.executable().toString(), command.guestExecutable());
    }

    @Test
    public void x86WaydroidSelectsGlibcLoader() throws Exception {
        LaunchConfig config = config(true);

        RunnerCommand command = RunnerCommand.forAbi("x86_64", config);

        assertTrue(command.arguments().contains("ld-linux-x86-64.so.2"));
    }

    private static LaunchConfig config(boolean waydroid) throws Exception {
        AppPaths paths = AppPaths.create(Files.createTempDirectory("dd1").toFile());
        return LaunchConfigFactory.create(
                new DeviceCaps(waydroid ? "x86_64" : "arm64-v8a", "GPU", waydroid),
                paths,
                null);
    }
}
