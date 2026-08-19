package io.github.dd1android.launcher.runtime;

import java.util.List;

public record RunnerCommand(
        String packagedExecutable, String guestExecutable, List<String> arguments) {
    public RunnerCommand {
        arguments = List.copyOf(arguments);
    }

    public static RunnerCommand forAbi(String abi, LaunchConfig config) {
        String guest = config.executable().toString();
        if ("arm64-v8a".equals(abi)) {
            return new RunnerCommand("libdd1_runner.so", guest, List.of(guest));
        }
        if ("x86_64".equals(abi)) {
            return new RunnerCommand(
                    "ld-linux-x86-64.so.2",
                    guest,
                    List.of("ld-linux-x86-64.so.2", "--library-path",
                            config.environment().get("BOX64_LD_LIBRARY_PATH"), guest));
        }
        throw new IllegalArgumentException("Unsupported ABI: " + abi);
    }
}
