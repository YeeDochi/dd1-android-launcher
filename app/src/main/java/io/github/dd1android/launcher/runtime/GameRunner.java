package io.github.dd1android.launcher.runtime;

import android.content.Context;
import android.os.Build;
import android.view.Surface;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class GameRunner {
    private final Context context;

    public GameRunner(Context context) {
        this.context = context.getApplicationContext();
    }

    public CompletableFuture<LaunchResult> launch(LaunchConfig config, Surface surface) {
        return CompletableFuture.supplyAsync(() -> run(config));
    }

    private LaunchResult run(LaunchConfig config) {
        long started = System.currentTimeMillis();
        File logs = new File(config.environment().get("BOX64_LOG_FILE")).getParentFile();
        logs.mkdirs();
        try {
            RunnerCommand command = RunnerCommand.forAbi(Build.SUPPORTED_ABIS[0], config);
            List<String> argv = new ArrayList<>();
            argv.add(new File(context.getApplicationInfo().nativeLibraryDir, "libdd1_runner.so")
                    .getAbsolutePath());
            if ("x86_64".equals(Build.SUPPORTED_ABIS[0])) {
                argv.add(new File(config.workingDirectory().getParent().toFile(),
                        "runtime/waydroid/lib/ld-linux-x86-64.so.2").getAbsolutePath());
                argv.addAll(command.arguments().subList(1, command.arguments().size()));
            } else {
                argv.addAll(command.arguments());
            }
            ProcessBuilder builder = new ProcessBuilder(argv)
                    .directory(config.workingDirectory().toFile())
                    .redirectErrorStream(true)
                    .redirectOutput(new File(logs, "runtime.log"));
            builder.environment().clear();
            builder.environment().putAll(config.environment());
            int exitCode = builder.start().waitFor();
            // ponytail: frame reporting arrives with the graphics bridge; process exit is enough here.
            return new LaunchResult(exitCode, config.renderer(), false,
                    System.currentTimeMillis() - started, logs.toPath());
        } catch (IOException error) {
            return new LaunchResult(126, config.renderer(), false,
                    System.currentTimeMillis() - started, logs.toPath());
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return new LaunchResult(130, config.renderer(), false,
                    System.currentTimeMillis() - started, logs.toPath());
        }
    }
}
