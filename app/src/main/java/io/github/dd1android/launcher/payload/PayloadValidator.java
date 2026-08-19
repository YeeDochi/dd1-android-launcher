package io.github.dd1android.launcher.payload;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class PayloadValidator {
    private static final String EXECUTABLE = "_linuxnosteam/darkest.bin.x86_64";
    private static final List<String> LIBRARIES = List.of(
            "libSDL2-2.0.so.0", "libfmod.so.14", "libfmodstudio.so.14");

    private PayloadValidator() {}

    public static ValidationResult validate(Path root) {
        List<String> errors = new ArrayList<>();
        Path executable = root.resolve(EXECUTABLE);
        if (!Files.isRegularFile(executable)) {
            errors.add("missing darkest.bin.x86_64");
        } else {
            try {
                ElfHeader header = ElfHeader.read(executable);
                if (!header.elf64LittleEndian() || header.machine() != 62) {
                    errors.add("darkest.bin.x86_64 is not an x86_64 ELF");
                }
            } catch (IOException error) {
                errors.add("cannot read darkest.bin.x86_64");
            }
        }
        for (String library : LIBRARIES) {
            if (!Files.isRegularFile(root.resolve("_linuxnosteam/lib64").resolve(library))) {
                errors.add("missing " + library);
            }
        }
        if (!Files.isDirectory(root.resolve("campaign"))) {
            errors.add("missing campaign/");
        }
        return new ValidationResult(errors.isEmpty(), errors);
    }
}
